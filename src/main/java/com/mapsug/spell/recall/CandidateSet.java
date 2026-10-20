package com.mapsug.spell.recall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Candidates of one token, de-duplicated by output key; a key found by several channels keeps all sources. */
public final class CandidateSet {

    private final Map<String, Candidate> byKey = new LinkedHashMap<>();

    public void add(Candidate c) {
        Candidate existing = byKey.get(c.key);
        if (existing == null) {
            byKey.put(c.key, c);
            return;
        }
        existing.sources.addAll(c.sources);
        if (c.distance < existing.distance) {
            existing.distance = c.distance;
            existing.alignTarget = c.alignTarget;
        }
        existing.knownCount = Math.max(existing.knownCount, c.knownCount);
    }

    public boolean contains(String key) {
        return byKey.containsKey(key);
    }

    public int size() {
        return byKey.size();
    }

    public List<Candidate> list() {
        return new ArrayList<>(byKey.values());
    }
}
