package com.mapsug.spell.dict;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Word bigram model over clean place text (Google Maps Top1 names/addresses and clicked results).
 * It supplies the context signal for the decoder: "de moaalos" is unlikely, "de morelos" is
 * frequent. Scores use stupid backoff, and {@link #gain} reports how much the left context
 * raises a word above its unigram probability so it can be added on top of the token-level
 * frequency feature without double counting.
 *
 * <p>Prefix variants handle the unfinished last token: P(prefix | prev) sums the continuations
 * of {@code prev} that start with the prefix.
 */
public final class BigramModel {

    private static final double BACKOFF = Math.log(0.4);
    private static final int MAX_CONTINUATIONS = 256;

    private final Map<String, Long> unigrams = new HashMap<>();
    private final Map<String, Map<String, Long>> bigrams = new HashMap<>();
    private final Map<String, long[]> leftTotals = new HashMap<>();
    private final PrefixTrie unigramTrie = new PrefixTrie();
    private Map<String, List<Map.Entry<String, Long>>> continuations = Map.of();
    private long total;
    private boolean frozen;

    public void addSentence(List<String> words, long weight) {
        if (frozen) {
            throw new IllegalStateException("model already frozen");
        }
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            unigrams.merge(w, weight, Long::sum);
            total += weight;
            if (i > 0) {
                String prev = words.get(i - 1);
                bigrams.computeIfAbsent(prev, k -> new HashMap<>()).merge(w, weight, Long::sum);
                leftTotals.computeIfAbsent(prev, k -> new long[1])[0] += weight;
            }
        }
    }

    public BigramModel freeze() {
        int id = 0;
        for (Map.Entry<String, Long> e : unigrams.entrySet()) {
            unigramTrie.insert(e.getKey(), id++, e.getValue());
        }
        Map<String, List<Map.Entry<String, Long>>> cont = new HashMap<>();
        for (Map.Entry<String, Map<String, Long>> e : bigrams.entrySet()) {
            List<Map.Entry<String, Long>> l = new ArrayList<>(e.getValue().entrySet());
            l.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            cont.put(e.getKey(), l.size() > MAX_CONTINUATIONS ? new ArrayList<>(l.subList(0, MAX_CONTINUATIONS)) : l);
        }
        continuations = cont;
        frozen = true;
        return this;
    }

    private double logUnigram(String w) {
        long c = unigrams.getOrDefault(w, 0L);
        return Math.log((c + 0.5) / (total + 0.5 * Math.max(1, unigrams.size())));
    }

    private double logUnigramPrefix(String p) {
        long c = unigramTrie.prefixFreq(p);
        return Math.log((c + 0.5) / (total + 0.5 * Math.max(1, unigrams.size())));
    }

    public double logProb(String prev, String w) {
        Map<String, Long> m = bigrams.get(prev);
        if (m != null) {
            Long c = m.get(w);
            if (c != null) {
                return Math.log((double) c / leftTotals.get(prev)[0]);
            }
        }
        return BACKOFF + logUnigram(w);
    }

    public double logProbPrefix(String prev, String prefix) {
        List<Map.Entry<String, Long>> l = continuations.get(prev);
        if (l != null) {
            long sum = 0;
            for (Map.Entry<String, Long> e : l) {
                if (e.getKey().startsWith(prefix)) {
                    sum += e.getValue();
                }
            }
            if (sum > 0) {
                return Math.log((double) sum / leftTotals.get(prev)[0]);
            }
        }
        return BACKOFF + logUnigramPrefix(prefix);
    }

    /** log P(w | prev) - log P(w): positive when the context supports {@code w}. */
    public double gain(String prev, String w, boolean prefix) {
        if (prev == null || total == 0) {
            return 0;
        }
        return prefix ? logProbPrefix(prev, w) - logUnigramPrefix(w) : logProb(prev, w) - logUnigram(w);
    }

    public int vocabularySize() {
        return unigrams.size();
    }

    public int bigramCount() {
        int n = 0;
        for (Map<String, Long> m : bigrams.values()) {
            n += m.size();
        }
        return n;
    }
}
