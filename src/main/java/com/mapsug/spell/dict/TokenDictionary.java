package com.mapsug.spell.dict;

import com.mapsug.spell.text.TextNormalizer;

import java.util.HashMap;
import java.util.Map;

/**
 * Vocabulary of one country, built from the historical token statistics (document frequency)
 * plus tokens of Google Maps Top1 results. Keys are folded; for each key the most frequent
 * surface form is kept for display (accents, apostrophes).
 */
public final class TokenDictionary {

    private final String[] keys;
    private final String[] displays;
    private final long[] freqs;
    private final Map<String, Integer> index;
    private final PrefixTrie trie;
    private final long totalFreq;

    private TokenDictionary(String[] keys, String[] displays, long[] freqs) {
        this.keys = keys;
        this.displays = displays;
        this.freqs = freqs;
        this.index = new HashMap<>(keys.length * 2);
        this.trie = new PrefixTrie();
        long total = 0;
        for (int i = 0; i < keys.length; i++) {
            index.put(keys[i], i);
            trie.insert(keys[i], i, freqs[i]);
            total += freqs[i];
        }
        this.totalFreq = total;
    }

    public int size() {
        return keys.length;
    }

    public int id(String key) {
        Integer id = index.get(key);
        return id == null ? -1 : id;
    }

    public String key(int id) {
        return keys[id];
    }

    public String display(int id) {
        return displays[id];
    }

    public long freq(int id) {
        return freqs[id];
    }

    public long freq(String key) {
        int id = id(key);
        return id < 0 ? 0 : freqs[id];
    }

    public long prefixFreq(String prefix) {
        return trie.prefixFreq(prefix);
    }

    public long totalFreq() {
        return totalFreq;
    }

    public PrefixTrie trie() {
        return trie;
    }

    /** Display form for a folded prefix, borrowed from the most frequent word continuing it. */
    public String displayPrefix(String foldedPrefix) {
        int best = trie.bestWordWithPrefix(foldedPrefix);
        if (best < 0) {
            return foldedPrefix;
        }
        String display = TextNormalizer.prefixByFoldedLength(displays[best], foldedPrefix.length());
        return TextNormalizer.fold(display).equals(foldedPrefix) ? display : foldedPrefix;
    }

    public String displayWord(String key) {
        int id = id(key);
        return id < 0 ? key : displays[id];
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, long[]> freq = new HashMap<>();
        private final Map<String, Map<String, Long>> surfaces = new HashMap<>();

        public Builder add(String surface, long df) {
            String lower = surface.toLowerCase(java.util.Locale.ROOT);
            String key = TextNormalizer.fold(lower);
            if (key.isEmpty() || df <= 0) {
                return this;
            }
            freq.computeIfAbsent(key, k -> new long[1])[0] += df;
            surfaces.computeIfAbsent(key, k -> new HashMap<>()).merge(lower, df, Long::sum);
            return this;
        }

        public TokenDictionary build(long minFreq) {
            int n = 0;
            for (long[] v : freq.values()) {
                if (v[0] >= minFreq) {
                    n++;
                }
            }
            String[] keys = new String[n];
            String[] displays = new String[n];
            long[] freqs = new long[n];
            int i = 0;
            for (Map.Entry<String, long[]> e : freq.entrySet()) {
                if (e.getValue()[0] < minFreq) {
                    continue;
                }
                keys[i] = e.getKey();
                freqs[i] = e.getValue()[0];
                displays[i] = surfaces.get(e.getKey()).entrySet().stream()
                        .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(e.getKey());
                i++;
            }
            return new TokenDictionary(keys, displays, freqs);
        }
    }
}
