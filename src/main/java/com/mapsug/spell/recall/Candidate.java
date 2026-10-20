package com.mapsug.spell.recall;

import java.util.EnumSet;

/** One correction hypothesis for one token. */
public final class Candidate {

    public enum Source { ORIGINAL, KNOWN, SYMSPELL, FUZZY_PREFIX, PHONETIC, SPLIT }

    /** Folded output; a prefix when {@link #prefix}, two words separated by a space when {@link #split}. */
    public final String key;
    /** Text to show (accents restored from the dictionary). */
    public final String surface;
    /** Folded string the input is aligned against for distance/channel (a full trie path for prefixes). */
    public String alignTarget;
    public final boolean prefix;
    public final boolean original;
    public final boolean split;
    public final long freq;
    public int distance;
    public long knownCount;
    public final EnumSet<Source> sources = EnumSet.noneOf(Source.class);
    public double[] features;
    public double score;

    public Candidate(String key, String surface, String alignTarget, boolean prefix, boolean original,
                     boolean split, long freq, int distance, Source source) {
        this.key = key;
        this.surface = surface;
        this.alignTarget = alignTarget;
        this.prefix = prefix;
        this.original = original;
        this.split = split;
        this.freq = freq;
        this.distance = distance;
        this.sources.add(source);
    }

    /** Word the left neighbour transitions into. */
    public String firstWord() {
        if (!split) {
            return key;
        }
        return key.substring(0, key.indexOf(' '));
    }

    /** Word the right neighbour is conditioned on. */
    public String lastWord() {
        if (!split) {
            return key;
        }
        return key.substring(key.indexOf(' ') + 1);
    }

    @Override
    public String toString() {
        return key + sources + String.format("(d=%d f=%d s=%.2f)", distance, freq, score);
    }
}
