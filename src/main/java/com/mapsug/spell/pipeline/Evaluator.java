package com.mapsug.spell.pipeline;

import com.mapsug.spell.core.CorrectionResult;
import com.mapsug.spell.core.CountryResources;
import com.mapsug.spell.core.SpellCorrector;
import com.mapsug.spell.data.Records;
import com.mapsug.spell.data.TripleCleaner;
import com.mapsug.spell.data.TripleExtractor;
import com.mapsug.spell.rank.RankerModel;
import com.mapsug.spell.recall.Candidate;
import com.mapsug.spell.text.Token;
import com.mapsug.spell.text.Tokenizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Query-level and token-level evaluation.
 *
 * <p>Query-level reference: the raw query with the plausible fixes extracted against its
 * Google Maps Top1 applied (the same alignment that produced the triples, filtered by the same
 * denoising rules). A correction counts as right only if the whole folded output equals the
 * reference, so completing a prefix ("appl" -> "apple") counts as wrong.
 */
public final class Evaluator {

    public record QueryCase(String query, String reference, boolean needsFix) {
    }

    public record QueryMetrics(int total, int needFix, int clean, int fixedRight, int fixedWrong, int missed,
                               int falsePositive, int overCompletion, double p50Micros, double p95Micros,
                               double p99Micros, double maxMicros) {

        public double precision() {
            int changed = fixedRight + fixedWrong + falsePositive;
            return changed == 0 ? 1 : (double) fixedRight / changed;
        }

        public double recall() {
            return needFix == 0 ? 0 : (double) fixedRight / needFix;
        }

        public double f05() {
            double p = precision();
            double r = recall();
            return p + r == 0 ? 0 : 1.25 * p * r / (0.25 * p + r);
        }

        public double falsePositiveRate() {
            return clean == 0 ? 0 : (double) falsePositive / clean;
        }

        public double accuracy() {
            return total == 0 ? 0 : (double) (total - fixedWrong - missed - falsePositive) / total;
        }

        @Override
        public String toString() {
            return String.format(
                    "queries=%d needFix=%d | precision=%.3f recall=%.3f F0.5=%.3f accuracy=%.3f FPR=%.4f overCompletion=%d"
                            + " | latency p50=%.0fus p95=%.0fus p99=%.0fus max=%.0fus",
                    total, needFix, precision(), recall(), f05(), accuracy(), falsePositiveRate(), overCompletion,
                    p50Micros, p95Micros, p99Micros, maxMicros);
        }
    }

    public record TokenMetrics(int total, int recalled, int top1, int unchanged) {

        public double recallCoverage() {
            return total == 0 ? 0 : (double) recalled / total;
        }

        public double accuracy() {
            return total == 0 ? 0 : (double) top1 / total;
        }

        @Override
        public String toString() {
            return String.format("typos=%d | recall@candidates=%.3f top1-accuracy=%.3f left-unchanged=%d",
                    total, recallCoverage(), accuracy(), unchanged);
        }
    }

    /** Queries whose tokens mostly do not align with the Top1 are excluded: the Top1 is likely another place. */
    static final double MIN_ALIGNED = 0.5;

    private Evaluator() {
    }

    public static List<QueryCase> buildCases(List<Records.QueryTop1> rows, CountryResources res) {
        List<QueryCase> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Records.QueryTop1 r : rows) {
            if (!seen.add(r.query())) {
                continue;
            }
            List<TripleExtractor.Fix> all = TripleExtractor.extract(r.query(), r.top1Name(), r.top1Address());
            if (TripleExtractor.alignedFraction(r.query(), r.top1Name(), r.top1Address(), all) < MIN_ALIGNED) {
                continue;
            }
            List<TripleExtractor.Fix> fixes = new ArrayList<>();
            for (TripleExtractor.Fix f : all) {
                if (TripleCleaner.check(f.wrong(), f.right(), res.dict, res.trustFreq, res.config.noiseWrongRightRatio) == null) {
                    fixes.add(f);
                }
            }
            out.add(new QueryCase(r.query(), TripleExtractor.referenceQuery(r.query(), fixes), !fixes.isEmpty()));
        }
        return out;
    }

    public static List<SpellCorrector.Analysis> analyze(SpellCorrector sc, List<QueryCase> cases) {
        List<SpellCorrector.Analysis> out = new ArrayList<>(cases.size());
        for (QueryCase c : cases) {
            out.add(sc.analyze(c.query()));
        }
        return out;
    }

    /** Scores precomputed analyses (fast; used for tuning). Latency fields are 0. */
    public static QueryMetrics score(SpellCorrector sc, List<QueryCase> cases, List<SpellCorrector.Analysis> analyses,
                                     RankerModel m) {
        Counter k = new Counter();
        for (int i = 0; i < cases.size(); i++) {
            k.add(cases.get(i), sc.decide(analyses.get(i), m));
        }
        return k.metrics(new double[0]);
    }

    public record ErrorCase(String kind, String query, String reference, String output) {
    }

    /** Full end-to-end run including recall, with per-query latency. */
    public static QueryMetrics evaluate(SpellCorrector sc, List<QueryCase> cases, RankerModel m) {
        return evaluate(sc, cases, m, null);
    }

    /** As {@link #evaluate(SpellCorrector, List, RankerModel)}, collecting misfires into {@code errors} when non-null. */
    public static QueryMetrics evaluate(SpellCorrector sc, List<QueryCase> cases, RankerModel m, List<ErrorCase> errors) {
        Counter k = new Counter();
        double[] lat = new double[cases.size()];
        for (int i = 0; i < cases.size(); i++) {
            long t0 = System.nanoTime();
            CorrectionResult r = sc.decide(sc.analyze(cases.get(i).query()), m);
            lat[i] = (System.nanoTime() - t0) / 1000.0;
            String kind = k.add(cases.get(i), r);
            if (errors != null && kind != null) {
                errors.add(new ErrorCase(kind, cases.get(i).query(), cases.get(i).reference(), r.corrected()));
            }
        }
        return k.metrics(lat);
    }

    public static TokenMetrics evaluateTokens(SpellCorrector sc, List<Records.Triple> triples, RankerModel m) {
        int total = 0;
        int recalled = 0;
        int top1 = 0;
        int unchanged = 0;
        CountryResources res = sc.resources();
        for (Records.Triple t : triples) {
            boolean complete = res.dict.freq(t.right()) > 0 || res.dict.prefixFreq(t.right()) == 0;
            String q = complete ? t.wrong() + " " : t.wrong();
            SpellCorrector.Analysis a = sc.analyze(q);
            if (a.tokens().size() != 1) {
                continue;
            }
            total++;
            List<Candidate> cands = a.lattice().get(0);
            if (cands.stream().anyMatch(c -> c.key.equals(t.right()))) {
                recalled++;
            }
            CorrectionResult r = sc.decide(a, m);
            String out = String.join(" ", Tokenizer.words(r.corrected()));
            if (out.equals(t.right())) {
                top1++;
            } else if (!r.changed()) {
                unchanged++;
            }
        }
        return new TokenMetrics(total, recalled, top1, unchanged);
    }

    private static final class Counter {
        int total, needFix, clean, right, wrong, missed, fp, over;

        /** Returns the error kind, or null when the output is right. */
        String add(QueryCase c, CorrectionResult r) {
            total++;
            String out = String.join(" ", Tokenizer.words(r.corrected()));
            List<Token> in = Tokenizer.tokenize(c.query());
            List<Token> res = Tokenizer.tokenize(r.corrected());
            if (!in.isEmpty() && in.size() == res.size() && !in.get(in.size() - 1).complete()
                    && res.get(res.size() - 1).norm().length() > in.get(in.size() - 1).norm().length()) {
                over++;
            }
            if (c.needsFix()) {
                needFix++;
                if (out.equals(c.reference())) {
                    right++;
                    return null;
                }
                if (r.changed()) {
                    wrong++;
                    return "wrong_fix";
                }
                missed++;
                return "missed";
            }
            clean++;
            if (r.changed()) {
                fp++;
                return "false_positive";
            }
            return null;
        }

        QueryMetrics metrics(double[] lat) {
            double[] s = lat.clone();
            Arrays.sort(s);
            return new QueryMetrics(total, needFix, clean, right, wrong, missed, fp, over,
                    pct(s, 0.50), pct(s, 0.95), pct(s, 0.99), s.length == 0 ? 0 : s[s.length - 1]);
        }

        private static double pct(double[] s, double p) {
            if (s.length == 0) {
                return 0;
            }
            return s[Math.min(s.length - 1, (int) Math.ceil(p * s.length) - 1)];
        }
    }
}
