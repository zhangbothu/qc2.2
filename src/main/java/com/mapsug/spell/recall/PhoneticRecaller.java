package com.mapsug.spell.recall;

import com.mapsug.spell.core.CountryResources;
import com.mapsug.spell.text.EditDistance;
import com.mapsug.spell.text.Phonetic;
import com.mapsug.spell.text.Token;

/** Sound-alike recall for complete tokens: words sharing the language-specific phonetic key. */
public final class PhoneticRecaller implements Recaller {

    @Override
    public void recall(Token token, CountryResources res, CandidateSet out) {
        if (!token.complete() || token.length() < 4) {
            return;
        }
        int[] ids = res.phoneticIndex.get(Phonetic.key(token.norm(), res.profile.language()));
        if (ids == null) {
            return;
        }
        for (int id : ids) {
            String w = res.dict.key(id);
            if (w.equals(token.norm())) {
                continue;
            }
            int d = EditDistance.osa(token.norm(), w, 3);
            if (d > 3) {
                continue;
            }
            out.add(new Candidate(w, res.dict.display(id), w, false, false, false, res.dict.freq(id), d,
                    Candidate.Source.PHONETIC));
        }
    }
}
