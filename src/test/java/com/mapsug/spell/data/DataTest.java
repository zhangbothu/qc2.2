package com.mapsug.spell.data;

import com.mapsug.spell.dict.TokenDictionary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataTest {

    @Test
    void extractorAlignsUnfinishedTokensToSameLengthPrefixes() {
        List<TripleExtractor.Fix> f = TripleExtractor.extract("starbucks coefe", "Starbucks Coffee", "");
        assertEquals(1, f.size());
        assertEquals("coffe", f.get(0).right());
        assertEquals("appl", TripleExtractor.extract("aple", "Apple Store", "").get(0).right());
        assertEquals("morelos", TripleExtractor.extract("ecatepec de moaalos ", "Ecatepec de Morelos", "").get(0).right());
    }

    @Test
    void cleanerDropsTriplesWhoseWrongSideIsAFrequentWord() {
        TokenDictionary d = TokenDictionary.builder().add("london", 900).add("lond", 200).add("coffee", 300).build(1);
        TripleCleaner.Result r = TripleCleaner.clean(List.of(
                new Records.Triple("GB", "lond", "london", 2, 5),
                new Records.Triple("GB", "cofee", "coffee", 1, 3),
                new Records.Triple("GB", "abc", "xyzzy", 5, 1)), d, 50, 0.2);
        assertEquals(1, r.kept().size());
        assertEquals("cofee", r.kept().get(0).wrong());
        assertTrue(r.dropReasons().containsKey("wrong_is_valid_word"));
        assertTrue(r.dropReasons().containsKey("distance_too_large"));
    }

    @Test
    void tsvRoundTripKeepsTrailingSpaceOfQueries(@TempDir Path dir) throws Exception {
        Dataset d = new Dataset(
                List.of(new Records.QueryTop1("MX", "banco asteca ", "Banco Azteca", "Ecatepec")),
                List.of(new Records.Triple("MX", "asteca", "azteca", 1, 3)),
                List.of(new Records.TokenStat("MX", "azteca", 10)),
                List.of(new Records.QueryClick("MX", "banco asteca ", "Banco Azteca", 2)));
        d.write(dir);
        Dataset back = Dataset.load(dir);
        assertEquals("banco asteca ", back.queryTop1().get(0).query());
        assertEquals(3, back.triples().get(0).count());
        assertEquals(List.of("MX"), back.countries());
    }
}
