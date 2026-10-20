package com.mapsug.spell.recall;

import com.mapsug.spell.core.CountryResources;
import com.mapsug.spell.text.Token;

/**
 * Missing-space recall: "starbuckscoffee" -> "starbucks coffee", and for the unfinished token
 * "starbuckscof" -> "starbucks cof" (the right part only needs to be a valid prefix).
 */
public final class SplitRecaller implements Recaller {

    @Override
    public void recall(Token token, CountryResources res, CandidateSet out) {
        String q = token.norm();
        if (q.length() < 6) {
            return;
        }
        long minFreq = Math.max(5, res.trustFreq / 4);
        String bestKey = null;
        String bestSurface = null;
        long bestMin = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 3; i <= q.length() - 2; i++) {
            String left = q.substring(0, i);
            String right = q.substring(i);
            long fl = res.dict.freq(left);
            if (fl < minFreq) {
                continue;
            }
            long fr = token.complete() ? res.dict.freq(right) : res.dict.prefixFreq(right);
            if (fr < minFreq) {
                continue;
            }
            double s = Math.log(fl) + Math.log(fr);
            if (s > bestScore) {
                bestScore = s;
                bestKey = left + " " + right;
                bestSurface = res.dict.displayWord(left) + " "
                        + (token.complete() ? res.dict.displayWord(right) : res.dict.displayPrefix(right));
                bestMin = Math.min(fl, fr);
            }
        }
        if (bestKey != null) {
            out.add(new Candidate(bestKey, bestSurface, bestKey.replace(" ", ""), !token.complete(), false, true,
                    bestMin, 1, Candidate.Source.SPLIT));
        }
    }
}
