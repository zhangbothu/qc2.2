package com.mapsug.spell.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {

    @Test
    void osaCountsTranspositionAsOneEdit() {
        assertEquals(1, EditDistance.osa("starbukcs", "starbucks"));
        assertEquals(2, EditDistance.osa("moaalos", "morelos"));
        assertEquals(1, EditDistance.osa("aple", "apple"));
        assertEquals(3, EditDistance.osa("abcdef", "xyzdef", 2), "bounded distance returns max + 1");
    }

    @Test
    void alignmentRecoversEditOperations() {
        List<EditDistance.Op> ops = EditDistance.align("coefe", "coffe");
        assertEquals(1, ops.size());
        assertEquals(EditDistance.OpType.SUB, ops.get(0).type());
        assertEquals("f", ops.get(0).intended());
        assertEquals("e", ops.get(0).typed());
    }

    @Test
    void foldRemovesAccentsAndApostrophes() {
        assertEquals("juarez", TextNormalizer.fold("Juárez"));
        assertEquals("sainsburys", TextNormalizer.fold("Sainsbury's"));
        assertEquals("Nezahualcó", TextNormalizer.prefixByFoldedLength("Nezahualcóyotl", 10));
    }

    @Test
    void tokenizerMarksOnlyTheUnfinishedLastWordAsIncomplete() {
        List<Token> t = Tokenizer.tokenize("Starbucks coefe");
        assertEquals(2, t.size());
        assertTrue(t.get(0).complete());
        assertFalse(t.get(1).complete());
        assertTrue(Tokenizer.tokenize("starbucks coffee ").get(1).complete());
        assertEquals("king's", Tokenizer.tokenize("king's cross").get(0).raw());
        assertTrue(Tokenizer.tokenize("calle 5 de mayo 123").get(1).hasDigit());
    }

    @Test
    void phoneticKeysMergeSoundAlikeSpellings() {
        assertEquals(Phonetic.key("vallarta", Language.ES), Phonetic.key("bayarta", Language.ES));
        assertEquals(Phonetic.key("soekarno", Language.ID), Phonetic.key("sukarno", Language.ID));
        assertEquals(Phonetic.key("phoenix", Language.EN), Phonetic.key("foenix", Language.EN));
    }

    @Test
    void caseStyleIsPreserved() {
        assertEquals("Morelos", TextNormalizer.applyCase("Moaalos", "morelos"));
        assertEquals("COFFE", TextNormalizer.applyCase("COEFE", "coffe"));
        assertEquals("appl", TextNormalizer.applyCase("aple", "appl"));
    }
}
