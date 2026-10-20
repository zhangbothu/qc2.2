package com.mapsug.spell.core;

import java.util.List;

/**
 * Output of one correction.
 *
 * @param confidence logistic of (best path score - original path score - threshold); 0 when unchanged
 * @param degraded   true when the time budget was hit and some tokens were left uncorrected
 */
public record CorrectionResult(String country, String original, String corrected, boolean changed,
                               double confidence, double scoreMargin, long latencyMicros, boolean cached,
                               boolean degraded, List<TokenResult> tokens) {

    public record TokenResult(String original, String corrected, boolean complete, boolean changed,
                              String sources, int candidates, double score) {
    }

    public CorrectionResult withCached(long latencyMicros) {
        return new CorrectionResult(country, original, corrected, changed, confidence, scoreMargin, latencyMicros,
                true, degraded, tokens);
    }
}
