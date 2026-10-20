package com.mapsug.spell.data;

import com.mapsug.spell.dict.TokenDictionary;
import com.mapsug.spell.text.EditDistance;
import com.mapsug.spell.text.TextNormalizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Denoises the suspected-correction triples before they are used as training signal.
 * The triples come from aligning a query with a Google Maps Top1 that may be a different place
 * than the user wanted, so a "wrong" token is often a perfectly valid word. A triple is dropped
 * when:
 * <ul>
 *   <li>both sides fold to the same key, or either side contains digits;</li>
 *   <li>the edit distance is too large for the token length (rewrites, not typos);</li>
 *   <li>the right side is not a known word or a known prefix of one;</li>
 *   <li>the wrong side is itself a frequent word that is not much rarer than the right side
 *       (real-word conflicts such as "london" vs "lond", "mall" vs "malls").</li>
 * </ul>
 */
public final class TripleCleaner {

    public record Result(List<Records.Triple> kept, Map<String, Map<String, Long>> pairCounts, Map<String, Long> dropReasons) {
    }

    private TripleCleaner() {
    }

    public static Result clean(List<Records.Triple> triples, TokenDictionary dict, long trustFreq, double maxWrongRightRatio) {
        List<Records.Triple> kept = new ArrayList<>();
        Map<String, Map<String, Long>> pairs = new HashMap<>();
        Map<String, Long> reasons = new HashMap<>();
        for (Records.Triple t : triples) {
            String w = TextNormalizer.fold(t.wrong().toLowerCase(java.util.Locale.ROOT));
            String r = TextNormalizer.fold(t.right().toLowerCase(java.util.Locale.ROOT));
            String reason = check(w, r, dict, trustFreq, maxWrongRightRatio);
            if (reason != null) {
                reasons.merge(reason, t.count(), Long::sum);
                continue;
            }
            kept.add(new Records.Triple(t.country(), w, r, EditDistance.osa(w, r), t.count()));
            pairs.computeIfAbsent(w, k -> new HashMap<>()).merge(r, t.count(), Long::sum);
        }
        return new Result(kept, pairs, reasons);
    }

    /** Reason for rejecting the pair (folded inputs), or null when it looks like a genuine typo. */
    public static String check(String w, String r, TokenDictionary dict, long trustFreq, double ratio) {
        if (w.isEmpty() || r.isEmpty() || w.equals(r)) {
            return "identical";
        }
        if (w.chars().anyMatch(Character::isDigit) || r.chars().anyMatch(Character::isDigit)) {
            return "numeric";
        }
        int max = TripleExtractor.maxDistance(Math.max(w.length(), r.length()));
        if (EditDistance.osa(w, r, max) > max) {
            return "distance_too_large";
        }
        long rightFreq = Math.max(dict.freq(r), dict.prefixFreq(r));
        if (rightFreq == 0) {
            return "right_unknown";
        }
        long wrongFreq = dict.freq(w);
        if (wrongFreq >= trustFreq && wrongFreq >= rightFreq * ratio) {
            return "wrong_is_valid_word";
        }
        return null;
    }
}
