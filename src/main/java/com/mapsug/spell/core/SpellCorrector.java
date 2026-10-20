package com.mapsug.spell.core;

import com.mapsug.spell.decode.BeamDecoder;
import com.mapsug.spell.rank.FeatureExtractor;
import com.mapsug.spell.rank.RankerModel;
import com.mapsug.spell.recall.Candidate;
import com.mapsug.spell.recall.RecallEngine;
import com.mapsug.spell.text.TextNormalizer;
import com.mapsug.spell.text.Token;
import com.mapsug.spell.text.Tokenizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Online correction for one country: tokenize -> gate -> multi-channel recall -> feature
 * extraction and log-linear token scoring -> beam decoding with bigram context -> accept only if
 * the best path beats the untouched query by the threshold. Thread-safe.
 */
public final class SpellCorrector {

    /** Tokens with their candidate lists and features; independent of the ranker weights. */
    public record Analysis(String query, List<Token> tokens, List<List<Candidate>> lattice, boolean degraded) {
    }

    private final CountryResources res;
    private volatile RankerModel model;
    private final RecallEngine recall;
    private final BeamDecoder decoder;
    private final Map<String, CorrectionResult> cache;

    public SpellCorrector(CountryResources res, RankerModel model) {
        this.res = res;
        this.model = model;
        this.recall = RecallEngine.standard();
        this.decoder = new BeamDecoder(res.bigram);
        int cap = Math.max(0, res.config.cacheSize);
        this.cache = java.util.Collections.synchronizedMap(new LinkedHashMap<>(1024, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CorrectionResult> e) {
                return size() > cap;
            }
        });
    }

    public CountryResources resources() {
        return res;
    }

    public RankerModel model() {
        return model;
    }

    public void setModel(RankerModel model) {
        this.model = model;
        cache.clear();
    }

    public CorrectionResult correct(String query) {
        long t0 = System.nanoTime();
        if (res.config.cacheSize > 0) {
            CorrectionResult hit = cache.get(query);
            if (hit != null) {
                return hit.withCached((System.nanoTime() - t0) / 1000);
            }
        }
        CorrectionResult r = decide(analyze(query), model, t0);
        if (res.config.cacheSize > 0 && !r.degraded()) {
            cache.put(query, r);
        }
        return r;
    }

    public Analysis analyze(String query) {
        long deadline = System.nanoTime() + res.config.timeBudgetMillis * 1_000_000L;
        List<Token> tokens = Tokenizer.tokenize(query == null ? "" : query);
        List<List<Candidate>> lattice = new ArrayList<>(tokens.size());
        boolean degraded = false;
        for (Token t : tokens) {
            List<Candidate> cands;
            if (degraded || System.nanoTime() > deadline) {
                degraded = true;
                cands = new ArrayList<>(List.of(RecallEngine.original(t, res)));
            } else {
                cands = recall.recall(t, res);
            }
            Candidate orig = cands.get(0);
            for (Candidate c : cands) {
                c.features = FeatureExtractor.extract(t, c, orig, res);
            }
            lattice.add(cands);
        }
        return new Analysis(query, tokens, lattice, degraded);
    }

    public CorrectionResult decide(Analysis a, RankerModel m) {
        return decide(a, m, System.nanoTime());
    }

    private CorrectionResult decide(Analysis a, RankerModel m, long t0) {
        String query = a.query() == null ? "" : a.query();
        List<List<Candidate>> lattice = a.lattice();
        for (List<Candidate> cands : lattice) {
            for (Candidate c : cands) {
                c.score = m.score(c.features);
            }
        }
        int n = lattice.size();
        int[] originalChoice = new int[n];
        double origScore = decoder.score(lattice, originalChoice, m.contextWeight);
        BeamDecoder.Path best = n == 0 ? new BeamDecoder.Path(originalChoice, 0)
                : decoder.decode(lattice, m.contextWeight, res.config.beamWidth);
        double margin = best.score() - origScore;
        boolean accept = margin >= m.acceptThreshold && !java.util.Arrays.equals(best.choice(), originalChoice);
        int[] choice = accept ? best.choice() : originalChoice;

        StringBuilder out = new StringBuilder(query.length() + 8);
        List<CorrectionResult.TokenResult> details = new ArrayList<>(n);
        int cursor = 0;
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            Token t = a.tokens().get(i);
            Candidate c = lattice.get(i).get(choice[i]);
            out.append(query, cursor, t.start());
            String text = c.original ? t.raw() : TextNormalizer.applyCase(t.raw(), c.surface);
            out.append(text);
            cursor = t.end();
            changed |= !c.original;
            details.add(new CorrectionResult.TokenResult(t.raw(), text, t.complete(), !c.original,
                    c.sources.toString(), lattice.get(i).size(), c.score));
        }
        out.append(query.substring(Math.min(cursor, query.length())));
        double confidence = changed ? 1 / (1 + Math.exp(-(margin - m.acceptThreshold))) : 0;
        return new CorrectionResult(res.profile.code(), query, out.toString(), changed, confidence, margin,
                (System.nanoTime() - t0) / 1000, false, a.degraded(), details);
    }
}
