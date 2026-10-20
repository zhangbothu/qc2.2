package com.mapsug.spell.rank;

import com.mapsug.spell.core.CountryResources;
import com.mapsug.spell.recall.Candidate;
import com.mapsug.spell.text.Phonetic;
import com.mapsug.spell.text.Token;

/**
 * Token-level ranking features. Context is deliberately not here: it is scored jointly by the
 * decoder through the bigram model, so the token ranker can be trained from context-free triples.
 */
public final class FeatureExtractor {

    public static final String[] NAMES = {
            "is_original",
            "edit_distance",
            "relative_distance",
            "channel_logprob",
            "log_freq",
            "log_freq_gain",
            "known_log_count",
            "phonetic_match",
            "first_char_match",
            "length_diff",
            "is_split",
            "original_oov",
            "prefix_candidate",
    };

    public static final int SIZE = NAMES.length;
    public static final int KNOWN_LOG_COUNT = 6;

    private FeatureExtractor() {
    }

    public static double[] extract(Token token, Candidate c, Candidate original, CountryResources res) {
        double[] f = new double[SIZE];
        String q = token.norm();
        double origLogFreq = Math.log1p(original.freq);
        if (c.original) {
            f[0] = 1;
            f[4] = origLogFreq;
            f[7] = 1;
            f[8] = 1;
            f[11] = original.freq == 0 ? 1 : 0;
            return f;
        }
        String outKey = c.split ? c.key.replace(" ", "") : c.key;
        f[1] = c.distance;
        f[2] = (double) c.distance / Math.max(1, q.length());
        f[3] = res.errorModel.logChannel(q, c.alignTarget);
        f[4] = Math.log1p(c.freq);
        f[5] = f[4] - origLogFreq;
        f[6] = Math.log1p(c.knownCount);
        f[7] = Phonetic.key(q, res.profile.language()).equals(Phonetic.key(outKey, res.profile.language())) ? 1 : 0;
        f[8] = !outKey.isEmpty() && outKey.charAt(0) == q.charAt(0) ? 1 : 0;
        f[9] = Math.abs(outKey.length() - q.length());
        f[10] = c.split ? 1 : 0;
        f[12] = c.prefix ? 1 : 0;
        return f;
    }
}
