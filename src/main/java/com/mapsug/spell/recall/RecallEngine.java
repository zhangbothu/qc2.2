package com.mapsug.spell.recall;

import com.mapsug.spell.core.CountryResources;
import com.mapsug.spell.text.Token;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs the gating rules and the recall channels for one token and truncates the merged list
 * with a cheap prior, so the ranker sees at most {@code maxCandidatesPerToken} hypotheses.
 *
 * <p>Gating (no recall at all, only the original survives):
 * protected words and abbreviations, tokens with digits, tokens shorter than the minimum length,
 * complete tokens that are frequent words, unfinished tokens that are frequent prefixes, and
 * short unfinished tokens that are a reasonably attested prefix (three letters carry too little evidence
 * to overrule a prefix users really type, e.g. "izt" for Iztacalco).
 * This is what keeps legal prefixes such as "appl" or "ecatepec de mor" untouched.
 */
public final class RecallEngine {

    private static final Comparator<Candidate> PRIOR = Comparator
            .comparingInt((Candidate c) -> c.knownCount > 0 ? 0 : 1)
            .thenComparingInt(c -> c.distance)
            .thenComparing(c -> -c.freq);

    private final List<Recaller> recallers;

    public RecallEngine(List<Recaller> recallers) {
        this.recallers = recallers;
    }

    public static RecallEngine standard() {
        return new RecallEngine(List.of(
                new KnownCorrectionRecaller(),
                new SymSpellRecaller(),
                new FuzzyPrefixRecaller(),
                new PhoneticRecaller(),
                new SplitRecaller()));
    }

    public static Candidate original(Token token, CountryResources res) {
        boolean prefix = !token.complete();
        long freq = prefix ? res.dict.prefixFreq(token.norm()) : res.dict.freq(token.norm());
        return new Candidate(token.norm(), token.raw(), token.norm(), prefix, true, false, freq, 0,
                Candidate.Source.ORIGINAL);
    }

    public boolean gated(Token token, CountryResources res) {
        String q = token.norm();
        if (token.hasDigit() || q.length() < res.config.minTokenLength || res.profile.protectedTokens().contains(q)) {
            return true;
        }
        long freq = token.complete() ? res.dict.freq(q) : res.dict.prefixFreq(q);
        if (!token.complete() && q.length() <= res.config.shortPrefixMaxLength
                && freq >= Math.max(res.config.dictMinFreq, res.trustFreq * res.config.shortPrefixTrustRatio)) {
            return true;
        }
        return freq >= res.trustFreq;
    }

    /** Original first, then recalled candidates by prior. */
    public List<Candidate> recall(Token token, CountryResources res) {
        Candidate orig = original(token, res);
        List<Candidate> out = new ArrayList<>();
        out.add(orig);
        if (gated(token, res)) {
            return out;
        }
        CandidateSet set = new CandidateSet();
        for (Recaller r : recallers) {
            r.recall(token, res, set);
        }
        List<Candidate> recalled = set.list();
        recalled.removeIf(c -> c.key.equals(orig.key));
        recalled.sort(PRIOR);
        int cap = Math.max(0, res.config.maxCandidatesPerToken - 1);
        out.addAll(recalled.size() > cap ? recalled.subList(0, cap) : recalled);
        return out;
    }
}
