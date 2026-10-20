package com.mapsug.spell.recall;

import com.mapsug.spell.core.CountryResources;
import com.mapsug.spell.dict.SymSpellIndex;
import com.mapsug.spell.text.Token;

/** Whole-word edit-distance recall for complete tokens. */
public final class SymSpellRecaller implements Recaller {

    @Override
    public void recall(Token token, CountryResources res, CandidateSet out) {
        if (!token.complete()) {
            return;
        }
        int max = token.length() <= 4 ? 1 : 2;
        for (SymSpellIndex.Hit h : res.symSpell.lookup(token.norm(), max)) {
            String w = res.dict.key(h.wordId());
            if (w.equals(token.norm())) {
                continue;
            }
            out.add(new Candidate(w, res.dict.display(h.wordId()), w, false, false, false,
                    res.dict.freq(h.wordId()), h.distance(), Candidate.Source.SYMSPELL));
        }
    }
}
