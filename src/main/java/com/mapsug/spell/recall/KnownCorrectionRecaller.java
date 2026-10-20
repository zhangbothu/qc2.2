package com.mapsug.spell.recall;

import com.mapsug.spell.core.CountryResources;
import com.mapsug.spell.dict.KnownCorrections;
import com.mapsug.spell.text.EditDistance;
import com.mapsug.spell.text.Token;

/** Typos already seen in the cleaned triples. Prefix pairs ("aple" -> "appl") only apply to unfinished tokens. */
public final class KnownCorrectionRecaller implements Recaller {

    @Override
    public void recall(Token token, CountryResources res, CandidateSet out) {
        for (KnownCorrections.Entry e : res.known.lookup(token.norm())) {
            String right = e.right();
            boolean prefix = !token.complete();
            long freq;
            String surface;
            if (prefix) {
                if (right.length() > token.length()) {
                    continue;
                }
                freq = res.dict.prefixFreq(right);
                surface = res.dict.displayPrefix(right);
            } else {
                freq = res.dict.freq(right);
                if (freq == 0) {
                    continue;
                }
                surface = res.dict.displayWord(right);
            }
            Candidate c = new Candidate(right, surface, right, prefix, false, false, freq,
                    EditDistance.osa(token.norm(), right), Candidate.Source.KNOWN);
            c.knownCount = e.count();
            out.add(c);
        }
    }
}
