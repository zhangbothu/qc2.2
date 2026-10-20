package com.mapsug.spell.pipeline;

import com.mapsug.spell.core.CountryProfile;
import com.mapsug.spell.core.CountryResources;
import com.mapsug.spell.core.SpellConfig;
import com.mapsug.spell.core.SpellCorrector;
import com.mapsug.spell.data.Dataset;
import com.mapsug.spell.data.Records;
import com.mapsug.spell.data.TripleCleaner;
import com.mapsug.spell.data.TripleExtractor;
import com.mapsug.spell.rank.FeatureExtractor;
import com.mapsug.spell.rank.RankerModel;
import com.mapsug.spell.rank.RankerTrainer;
import com.mapsug.spell.recall.Candidate;
import com.mapsug.spell.text.Token;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Offline pipeline per country:
 * <ol>
 *   <li>split triples by wrong token and queries by query text (80% train / 10% dev / 10% test);</li>
 *   <li>build resources from all statistics but only training triples;</li>
 *   <li>build listwise examples: training typos (label = right token) and clean tokens of
 *       training queries (label = keep original);</li>
 *   <li>train the ranker, then grid-search context weight and accept threshold on dev queries
 *       for F0.5 (precision is worth more than recall: a bad rewrite breaks Sug);</li>
 *   <li>report on held-out test queries and test typos, save {@code <CC>.properties}.</li>
 * </ol>
 */
public final class TrainingPipeline {

    private static final double[] CONTEXT_GRID = {0, 0.25, 0.5, 1, 1.5, 2, 3};
    private static final double[] THRESHOLD_GRID = {-1, -0.5, 0, 0.5, 1, 1.5, 2, 2.5, 3, 4, 5};

    public record CountryReport(String country, String resources, int trainExamples, int recallMisses,
                                double trainTop1, RankerModel model, Evaluator.QueryMetrics baseline,
                                Evaluator.QueryMetrics test, Evaluator.TokenMetrics tokens) {

        public String render() {
            return "=== " + country + " ===\n"
                    + "resources : " + resources + "\n"
                    + String.format("training  : %d listwise examples, %d typos not recalled, train top1=%.3f%n",
                    trainExamples, recallMisses, trainTop1)
                    + "model     : " + model + "\n"
                    + "baseline  : " + baseline + "   (default weights)\n"
                    + "test      : " + test + "\n"
                    + "test typos: " + tokens + "\n";
        }
    }

    private final SpellConfig config;
    private List<Evaluator.ErrorCase> lastErrors = List.of();

    public TrainingPipeline(SpellConfig config) {
        this.config = config;
    }

    public List<CountryReport> run(Path dataDir, Path modelDir) throws IOException {
        Dataset all = Dataset.load(dataDir);
        List<CountryReport> reports = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (String cc : all.countries()) {
            if (!CountryProfile.isSupported(cc)) {
                continue;
            }
            CountryReport r = runCountry(CountryProfile.of(cc), all.forCountry(cc));
            r.model().save(modelDir.resolve(cc + ".properties"));
            StringBuilder err = new StringBuilder("kind\tquery\treference\toutput\n");
            for (Evaluator.ErrorCase e : lastErrors) {
                err.append(e.kind()).append('\t').append(e.query()).append('\t').append(e.reference()).append('\t')
                        .append(e.output()).append('\n');
            }
            Files.writeString(modelDir.resolve("errors_" + cc + ".tsv"), err.toString(), StandardCharsets.UTF_8);
            reports.add(r);
            System.out.println(r.render());
            text.append(r.render()).append('\n');
        }
        Files.createDirectories(modelDir);
        Files.writeString(modelDir.resolve("report.txt"), text.toString(), StandardCharsets.UTF_8);
        return reports;
    }

    public CountryReport runCountry(CountryProfile profile, Dataset data) {
        List<Records.Triple> trainTriples = new ArrayList<>();
        List<Records.Triple> testTriples = new ArrayList<>();
        for (Records.Triple t : data.triples()) {
            (Splits.of(t.wrong()) == Splits.Part.TRAIN ? trainTriples : testTriples).add(t);
        }
        List<Records.QueryTop1> trainQ = new ArrayList<>();
        List<Records.QueryTop1> devQ = new ArrayList<>();
        List<Records.QueryTop1> testQ = new ArrayList<>();
        for (Records.QueryTop1 q : data.queryTop1()) {
            switch (Splits.of(q.query())) {
                case TRAIN -> trainQ.add(q);
                case DEV -> devQ.add(q);
                case TEST -> testQ.add(q);
            }
        }

        CountryResources res = CountryResources.build(profile, data, trainTriples, config);
        SpellCorrector sc = new SpellCorrector(res, RankerModel.defaults());

        Random rnd = new Random(7);
        int[] misses = new int[1];
        List<RankerTrainer.Example> examples = new ArrayList<>(positives(sc, res, rnd, misses));
        examples.addAll(negatives(sc, res, trainQ, (int) (examples.size() * 1.5) + 50, rnd));

        RankerTrainer trainer = new RankerTrainer(25, 0.05, 1e-4, 11);
        double[] w = trainer.train(examples, RankerModel.defaults().weights);
        double trainTop1 = RankerTrainer.top1Accuracy(examples, w);

        List<Evaluator.QueryCase> devCases = Evaluator.buildCases(devQ, res);
        List<SpellCorrector.Analysis> devAnalyses = Evaluator.analyze(sc, devCases);
        RankerModel best = null;
        double bestF = -1;
        double bestFpr = 1;
        for (double cw : CONTEXT_GRID) {
            for (double th : THRESHOLD_GRID) {
                RankerModel m = new RankerModel(w.clone(), cw, th);
                Evaluator.QueryMetrics qm = Evaluator.score(sc, devCases, devAnalyses, m);
                double f = qm.f05();
                if (f > bestF + 1e-9 || (Math.abs(f - bestF) <= 1e-9 && qm.falsePositiveRate() < bestFpr)) {
                    bestF = f;
                    bestFpr = qm.falsePositiveRate();
                    best = m;
                }
            }
        }

        List<Evaluator.QueryCase> testCases = Evaluator.buildCases(testQ, res);
        Evaluator.QueryMetrics baseline = Evaluator.evaluate(sc, testCases, RankerModel.defaults());
        lastErrors = new ArrayList<>();
        Evaluator.QueryMetrics test = Evaluator.evaluate(sc, testCases, best, lastErrors);
        TripleCleaner.Result cleanedTest = TripleCleaner.clean(testTriples, res.dict, res.trustFreq, config.noiseWrongRightRatio);
        Evaluator.TokenMetrics tokens = Evaluator.evaluateTokens(sc, dedupe(cleanedTest.kept()), best);
        return new CountryReport(profile.code(), res.summary(), examples.size(), misses[0], trainTop1, best,
                baseline, test, tokens);
    }

    private static List<Records.Triple> dedupe(List<Records.Triple> triples) {
        Map<String, Records.Triple> m = new HashMap<>();
        for (Records.Triple t : triples) {
            m.putIfAbsent(t.wrong() + "\t" + t.right(), t);
        }
        return new ArrayList<>(m.values());
    }

    private List<RankerTrainer.Example> positives(SpellCorrector sc, CountryResources res, Random rnd, int[] misses) {
        Map<String, Long> pairs = new HashMap<>();
        for (Records.Triple t : res.cleanedTriples.kept()) {
            pairs.merge(t.wrong() + "\t" + t.right(), t.count(), Long::sum);
        }
        List<RankerTrainer.Example> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : pairs.entrySet()) {
            String[] wr = e.getKey().split("\t");
            String wrong = wr[0];
            String right = wr[1];
            boolean complete = res.dict.freq(right) > 0 || res.dict.prefixFreq(right) == 0;
            SpellCorrector.Analysis a = sc.analyze(complete ? wrong + " " : wrong);
            if (a.tokens().size() != 1) {
                continue;
            }
            List<Candidate> cands = a.lattice().get(0);
            if (cands.size() < 2) {
                continue;
            }
            int label = -1;
            for (int i = 0; i < cands.size(); i++) {
                if (cands.get(i).key.equals(right)) {
                    label = i;
                    break;
                }
            }
            if (label < 0) {
                misses[0]++;
                continue;
            }
            boolean dropKnown = rnd.nextBoolean();
            out.add(new RankerTrainer.Example(matrix(cands, dropKnown), label, 1 + Math.log(e.getValue())));
        }
        return out;
    }

    private List<RankerTrainer.Example> negatives(SpellCorrector sc, CountryResources res, List<Records.QueryTop1> rows,
                                                  int limit, Random rnd) {
        List<RankerTrainer.Example> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Records.QueryTop1> shuffled = new ArrayList<>(rows);
        java.util.Collections.shuffle(shuffled, rnd);
        for (Records.QueryTop1 r : shuffled) {
            if (out.size() >= limit) {
                break;
            }
            List<TripleExtractor.Fix> fixes = TripleExtractor.extract(r.query(), r.top1Name(), r.top1Address());
            if (TripleExtractor.alignedFraction(r.query(), r.top1Name(), r.top1Address(), fixes) < Evaluator.MIN_ALIGNED) {
                continue;
            }
            Set<Integer> fixed = new HashSet<>();
            for (TripleExtractor.Fix f : fixes) {
                fixed.add(f.tokenIndex());
            }
            SpellCorrector.Analysis a = sc.analyze(r.query());
            for (int i = 0; i < a.tokens().size(); i++) {
                Token t = a.tokens().get(i);
                List<Candidate> cands = a.lattice().get(i);
                if (fixed.contains(i) || cands.size() < 2 || !seen.add(t.norm() + (t.complete() ? " " : ""))) {
                    continue;
                }
                out.add(new RankerTrainer.Example(matrix(cands, false), 0, 1));
            }
        }
        return out;
    }

    private static double[][] matrix(List<Candidate> cands, boolean dropKnown) {
        double[][] f = new double[cands.size()][];
        for (int i = 0; i < cands.size(); i++) {
            f[i] = cands.get(i).features.clone();
            if (dropKnown) {
                f[i][FeatureExtractor.KNOWN_LOG_COUNT] = 0;
            }
        }
        return f;
    }
}
