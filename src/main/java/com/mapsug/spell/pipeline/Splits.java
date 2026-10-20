package com.mapsug.spell.pipeline;

import java.nio.charset.StandardCharsets;

/** Deterministic hash buckets: 0-7 train, 8 dev, 9 test. Keyed by text so splits are stable across runs. */
public final class Splits {

    public enum Part { TRAIN, DEV, TEST }

    private Splits() {
    }

    public static Part of(String key) {
        long h = 0xcbf29ce484222325L;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            h ^= b;
            h *= 0x100000001b3L;
        }
        int bucket = (int) Math.floorMod(h, 10L);
        return bucket < 8 ? Part.TRAIN : bucket == 8 ? Part.DEV : Part.TEST;
    }
}
