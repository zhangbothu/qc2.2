package com.mapsug.spell.core;

import com.mapsug.spell.data.Dataset;
import com.mapsug.spell.data.Records;
import com.mapsug.spell.rank.RankerModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellCorrectorTest {

    private static SpellCorrector mx;
    private static SpellCorrector gb;

    @BeforeAll
    static void setUp() {
        mx = corrector("MX", new String[][]{
                {"Ecatepec de Morelos", "Estado de México"},
                {"Walmart Ecatepec", "Ecatepec de Morelos"},
                {"Starbucks Coffee", "Paseo de la Reforma"},
                {"Naucalpan de Juárez", "Estado de México"},
                {"Plaza Satélite", "Naucalpan de Juárez"},
                {"Palacio de Hierro Polanco", "Polanco"},
                {"Calle Morales", "Polanco"},
        });
        gb = corrector("GB", new String[][]{
                {"Apple Store Regent Street", "London"},
                {"Starbucks Coffee", "Oxford Street"},
                {"Piccadilly Circus", "London"},
                {"King's Cross Station", "London"},
                {"Applebee's", "Leeds"},
        });
    }

    private static SpellCorrector corrector(String cc, String[][] places) {
        List<Records.QueryTop1> top1 = new ArrayList<>();
        List<Records.TokenStat> stats = new ArrayList<>();
        List<Records.QueryClick> clicks = new ArrayList<>();
        int pop = 400;
        for (String[] p : places) {
            top1.add(new Records.QueryTop1(cc, p[0].toLowerCase(), p[0], p[1]));
            clicks.add(new Records.QueryClick(cc, p[0].toLowerCase(), p[0], pop / 10));
            for (String w : (p[0] + " " + p[1]).split(" ")) {
                stats.add(new Records.TokenStat(cc, w, pop));
            }
            pop = Math.max(60, pop - 40);
        }
        stats.add(new Records.TokenStat(cc, "starbuks", 3));
        List<Records.Triple> triples = List.of(
                new Records.Triple(cc, "starbuks", "starbucks", 1, 12),
                new Records.Triple(cc, "cofee", "coffee", 1, 4));
        SpellConfig config = new SpellConfig();
        config.trustFreq = 150;
        Dataset d = new Dataset(top1, triples, stats, clicks);
        return new SpellCorrector(CountryResources.build(CountryProfile.of(cc), d, triples, config), RankerModel.defaults());
    }

    @Test
    void unfinishedTokenIsCorrectedToAPrefixNotCompleted() {
        assertEquals("appl", gb.correct("aple").corrected());
        assertEquals("starbucks coffe", gb.correct("starbucks coefe").corrected());
    }

    @Test
    void completeTokensAreCorrectedWithContext() {
        CorrectionResult r = mx.correct("Ecatepec de Moaalos ");
        assertEquals("Ecatepec de Morelos ", r.corrected());
        assertTrue(r.changed());
        assertEquals("Ecatepec de Morelos", mx.correct("Ecatepec de Moaalos").corrected());
    }

    @Test
    void knownTyposAndSoundAlikesAreFixed() {
        assertEquals("starbucks coffee ", mx.correct("starbuks coffee ").corrected());
        assertEquals("Palacio de Hierro Polanco ", mx.correct("Palacio de Hierro Polamco ").corrected());
    }

    @Test
    void legalPrefixesAndValidQueriesAreUntouched() {
        for (String q : List.of("ecatepec de mor", "appl", "starbucks cof", "naucalpan de ju", "plaza sat")) {
            CorrectionResult r = (q.startsWith("appl") ? gb : mx).correct(q);
            assertFalse(r.changed(), q + " -> " + r.corrected());
            assertEquals(q, r.corrected());
        }
    }

    @Test
    void numbersAndAbbreviationsAreProtected() {
        CorrectionResult r = mx.correct("av reforma 222 col juarez");
        assertEquals("av", r.tokens().get(0).corrected());
        assertEquals("222", r.tokens().get(2).corrected());
        assertEquals("col", r.tokens().get(3).corrected());
    }

    @Test
    void separatorsAndCaseArePreserved() {
        assertEquals("STARBUCKS, Coffee ", mx.correct("STARBUKS, Coffee ").corrected());
    }

    @Test
    void singleCorrectionStaysWellUnderLatencyBudget() {
        for (int i = 0; i < 500; i++) {
            mx.decide(mx.analyze("ecatepec de moaalos " + (i % 3 == 0 ? "" : "wal")), mx.model());
        }
        long t0 = System.nanoTime();
        int n = 2000;
        for (int i = 0; i < n; i++) {
            mx.decide(mx.analyze("palacio de hierro polamco " + i % 7), mx.model());
        }
        double avgMillis = (System.nanoTime() - t0) / 1e6 / n;
        assertTrue(avgMillis < 5, "average " + avgMillis + " ms");
    }

    @Test
    void emptyInputIsHandled() {
        assertEquals("", mx.correct("").corrected());
        assertEquals("   ", mx.correct("   ").corrected());
    }
}
