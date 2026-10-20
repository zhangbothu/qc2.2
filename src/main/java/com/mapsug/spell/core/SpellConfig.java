package com.mapsug.spell.core;

/** Tunables shared by all countries. Values here are defaults; trained models override the decoder knobs. */
public final class SpellConfig {

    /** Tokens with a smaller document frequency are not admitted into the vocabulary. */
    public long dictMinFreq = 2;
    /** Complete tokens / prefixes at least this frequent are trusted and not corrected; 0 = derive from data. */
    public long trustFreq = 0;
    public int symSpellMaxDistance = 2;
    public int symSpellPrefixLength = 7;
    public int minTokenLength = 3;
    /**
     * Unfinished tokens up to this length are kept when their prefix frequency reaches
     * {@code trustFreq * shortPrefixTrustRatio}; history logs contain typos, so "seen at all" is not enough.
     */
    public int shortPrefixMaxLength = 4;
    public double shortPrefixTrustRatio = 0.1;
    public int maxCandidatesPerToken = 24;
    public int fuzzyPrefixLimit = 48;
    public int phoneticBucketSize = 8;
    public int beamWidth = 8;
    /** Minimum support and share for a triple pair to enter the known-correction table. */
    public long knownMinCount = 2;
    public double knownMinShare = 0.6;
    /** Triples whose wrong side is at least this fraction as frequent as the right side are treated as noise. */
    public double noiseWrongRightRatio = 0.2;
    public double errorModelAlpha = 0.5;
    /** Internal budget for one correction; the request falls back to the original query when exceeded. */
    public long timeBudgetMillis = 30;
    public int cacheSize = 50_000;

    public SpellConfig copy() {
        SpellConfig c = new SpellConfig();
        c.dictMinFreq = dictMinFreq;
        c.trustFreq = trustFreq;
        c.symSpellMaxDistance = symSpellMaxDistance;
        c.symSpellPrefixLength = symSpellPrefixLength;
        c.minTokenLength = minTokenLength;
        c.shortPrefixMaxLength = shortPrefixMaxLength;
        c.shortPrefixTrustRatio = shortPrefixTrustRatio;
        c.maxCandidatesPerToken = maxCandidatesPerToken;
        c.fuzzyPrefixLimit = fuzzyPrefixLimit;
        c.phoneticBucketSize = phoneticBucketSize;
        c.beamWidth = beamWidth;
        c.knownMinCount = knownMinCount;
        c.knownMinShare = knownMinShare;
        c.noiseWrongRightRatio = noiseWrongRightRatio;
        c.errorModelAlpha = errorModelAlpha;
        c.timeBudgetMillis = timeBudgetMillis;
        c.cacheSize = cacheSize;
        return c;
    }
}
