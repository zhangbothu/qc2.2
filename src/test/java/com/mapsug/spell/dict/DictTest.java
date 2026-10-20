package com.mapsug.spell.dict;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DictTest {

    private static TokenDictionary dict() {
        return TokenDictionary.builder()
                .add("apple", 500).add("applebees", 20).add("store", 300)
                .add("coffee", 400).add("morelos", 250).add("Juárez", 100)
                .build(1);
    }

    @Test
    void prefixFrequencyAggregatesTheSubtree() {
        TokenDictionary d = dict();
        assertEquals(520, d.prefixFreq("appl"));
        assertEquals(0, d.prefixFreq("aple"));
        assertEquals("juár", d.displayPrefix("juar"));
    }

    @Test
    void fuzzyPrefixSearchFindsPathsWithinDistance() {
        List<PrefixTrie.Match> m = dict().trie().fuzzyPrefixSearch("aple", 1, 3, 5, 10);
        assertTrue(m.stream().anyMatch(x -> x.path().equals("apple") && x.distance() == 1), m.toString());
        List<PrefixTrie.Match> c = dict().trie().fuzzyPrefixSearch("coefe", 2, 3, 7, 10);
        assertTrue(c.stream().anyMatch(x -> x.path().equals("coffe")), c.toString());
    }

    @Test
    void symSpellFindsWholeWordCandidates() {
        TokenDictionary d = dict();
        SymSpellIndex s = new SymSpellIndex(d, 2, 7, 1);
        assertTrue(s.lookup("moaalos", 2).stream().anyMatch(h -> d.key(h.wordId()).equals("morelos")));
        assertTrue(s.lookup("stroe", 1).stream().anyMatch(h -> d.key(h.wordId()).equals("store")));
    }

    @Test
    void bigramGainRewardsContext() {
        BigramModel b = new BigramModel();
        b.addSentence(List.of("ecatepec", "de", "morelos"), 10);
        b.addSentence(List.of("naucalpan", "de", "juarez"), 10);
        b.addSentence(List.of("morales"), 30);
        b.freeze();
        assertTrue(b.gain("de", "morelos", false) > b.gain("de", "morales", false));
        assertTrue(b.gain("de", "mor", true) > 0);
    }

    @Test
    void errorModelPrefersLearnedAndAdjacentKeyEdits() {
        ErrorModel em = new ErrorModel(0.5);
        em.observe("coefe", "coffe", 20);
        assertTrue(em.logChannel("coefe", "coffe") > em.logChannel("coxfe", "coffe"));
        assertTrue(em.logChannel("cpffe", "coffe") > em.logChannel("czffe", "coffe"), "o/p are adjacent keys");
    }

    @Test
    void knownCorrectionsRequireSupportAndShare() {
        KnownCorrections k = KnownCorrections.build(Map.of(
                "starbuks", Map.of("starbucks", 9L, "starbuck", 1L),
                "lodnon", Map.of("london", 1L)), 2, 0.6);
        assertEquals("starbucks", k.lookup("starbuks").get(0).right());
        assertEquals(1, k.lookup("starbuks").size());
        assertTrue(k.lookup("lodnon").isEmpty());
    }
}
