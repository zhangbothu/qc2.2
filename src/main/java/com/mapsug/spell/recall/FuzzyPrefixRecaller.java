package com.mapsug.spell.recall;

import com.mapsug.spell.core.CountryResources;
import com.mapsug.spell.dict.PrefixTrie;
import com.mapsug.spell.text.Token;

/**
 * Recall for the unfinished last token. Searches trie paths within k edits of the typed prefix
 * and cuts every hit back to the typed length, so the output stays a prefix that Sug completes:
 * "aple" matches the path "apple" (1 edit) and yields "appl"; "coefe" matches "coffe"/"coffee"
 * and yields "coffe". The full path is kept as the alignment target for the error model.
 */
public final class FuzzyPrefixRecaller implements Recaller {

    @Override
    public void recall(Token token, CountryResources res, CandidateSet out) {
        if (token.complete()) {
            return;
        }
        String q = token.norm();
        int n = q.length();
        int max = n <= 4 ? 1 : 2;
        for (PrefixTrie.Match m : res.dict.trie().fuzzyPrefixSearch(q, max, Math.max(1, n - max), n + max,
                res.config.fuzzyPrefixLimit)) {
            if (m.distance() == 0) {
                continue;
            }
            String key = m.path().length() > n ? m.path().substring(0, n) : m.path();
            if (key.equals(q)) {
                continue;
            }
            out.add(new Candidate(key, res.dict.displayPrefix(key), m.path(), true, false, false,
                    res.dict.prefixFreq(key), m.distance(), Candidate.Source.FUZZY_PREFIX));
        }
    }
}
