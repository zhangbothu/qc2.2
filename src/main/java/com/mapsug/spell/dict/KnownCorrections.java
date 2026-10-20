package com.mapsug.spell.dict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-precision memory of frequent typos mined from the (cleaned) triples: wrong -> right with
 * support counts. It is one recall channel and one ranking feature; the ranker still decides.
 */
public final class KnownCorrections {

    public record Entry(String right, long count, double share) {
    }

    private final Map<String, List<Entry>> map;

    private KnownCorrections(Map<String, List<Entry>> map) {
        this.map = map;
    }

    public List<Entry> lookup(String wrong) {
        return map.getOrDefault(wrong, Collections.emptyList());
    }

    public long count(String wrong, String right) {
        for (Entry e : lookup(wrong)) {
            if (e.right.equals(right)) {
                return e.count;
            }
        }
        return 0;
    }

    public int size() {
        return map.size();
    }

    /**
     * @param pairCounts wrong -> (right -> count)
     * @param minCount   minimum support of a pair
     * @param minShare   minimum share of the pair among all rights observed for that wrong token
     */
    public static KnownCorrections build(Map<String, Map<String, Long>> pairCounts, long minCount, double minShare) {
        Map<String, List<Entry>> out = new HashMap<>();
        for (Map.Entry<String, Map<String, Long>> e : pairCounts.entrySet()) {
            long total = 0;
            for (long c : e.getValue().values()) {
                total += c;
            }
            List<Entry> l = new ArrayList<>();
            for (Map.Entry<String, Long> r : e.getValue().entrySet()) {
                double share = (double) r.getValue() / total;
                if (r.getValue() >= minCount && share >= minShare) {
                    l.add(new Entry(r.getKey(), r.getValue(), share));
                }
            }
            if (!l.isEmpty()) {
                l.sort((a, b) -> Long.compare(b.count, a.count));
                out.put(e.getKey(), l);
            }
        }
        return new KnownCorrections(out);
    }
}
