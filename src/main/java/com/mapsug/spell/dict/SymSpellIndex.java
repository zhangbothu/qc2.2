package com.mapsug.spell.dict;

import com.mapsug.spell.text.EditDistance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Symmetric-delete index (SymSpell) for complete tokens. Every dictionary word registers all
 * strings obtainable by deleting up to {@code maxDistance} characters from its first
 * {@code prefixLength} characters; a lookup generates the same deletes for the input and verifies
 * hits with the true OSA distance. Lookup cost is independent of vocabulary size, which is what
 * keeps recall in the sub-millisecond range.
 */
public final class SymSpellIndex {

    public record Hit(int wordId, int distance) {
    }

    private final TokenDictionary dict;
    private final int maxDistance;
    private final int prefixLength;
    private final Map<String, int[]> deletes;

    public SymSpellIndex(TokenDictionary dict, int maxDistance, int prefixLength, long minFreq) {
        this.dict = dict;
        this.maxDistance = maxDistance;
        this.prefixLength = prefixLength;
        Map<String, List<Integer>> tmp = new HashMap<>();
        Set<String> buf = new HashSet<>();
        for (int id = 0; id < dict.size(); id++) {
            if (dict.freq(id) < minFreq) {
                continue;
            }
            String key = dict.key(id);
            String p = key.length() > prefixLength ? key.substring(0, prefixLength) : key;
            buf.clear();
            buf.add(p);
            edits(p, 0, maxDistance, buf);
            for (String d : buf) {
                tmp.computeIfAbsent(d, k -> new ArrayList<>(2)).add(id);
            }
        }
        this.deletes = new HashMap<>(tmp.size() * 2);
        for (Map.Entry<String, List<Integer>> e : tmp.entrySet()) {
            List<Integer> l = e.getValue();
            int[] arr = new int[l.size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = l.get(i);
            }
            deletes.put(e.getKey(), arr);
        }
    }

    private static void edits(String word, int depth, int maxDistance, Set<String> out) {
        if (depth >= maxDistance || word.length() <= 1) {
            return;
        }
        for (int i = 0; i < word.length(); i++) {
            String d = word.substring(0, i) + word.substring(i + 1);
            if (out.add(d)) {
                edits(d, depth + 1, maxDistance, out);
            }
        }
    }

    public List<Hit> lookup(String input, int maxDist) {
        maxDist = Math.min(maxDist, maxDistance);
        String p = input.length() > prefixLength ? input.substring(0, prefixLength) : input;
        Set<String> probes = new HashSet<>();
        probes.add(p);
        edits(p, 0, maxDist, probes);
        Set<Integer> seen = new HashSet<>();
        List<Hit> hits = new ArrayList<>();
        for (String probe : probes) {
            int[] ids = deletes.get(probe);
            if (ids == null) {
                continue;
            }
            for (int id : ids) {
                if (!seen.add(id)) {
                    continue;
                }
                String w = dict.key(id);
                if (Math.abs(w.length() - input.length()) > maxDist) {
                    continue;
                }
                int d = EditDistance.osa(input, w, maxDist);
                if (d <= maxDist) {
                    hits.add(new Hit(id, d));
                }
            }
        }
        return hits;
    }

    public int entries() {
        return deletes.size();
    }
}
