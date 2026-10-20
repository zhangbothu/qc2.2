package com.mapsug.spell.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a Sug query into word tokens while remembering offsets so the corrected query can be
 * rebuilt with the user's original separators. Apostrophes inside a word are kept
 * ("king's"), every other non letter/digit character is a separator.
 */
public final class Tokenizer {

    private Tokenizer() {
    }

    public static List<Token> tokenize(String query) {
        List<Token> out = new ArrayList<>();
        int n = query.length();
        int i = 0;
        while (i < n) {
            while (i < n && !isWordChar(query, i)) {
                i++;
            }
            if (i >= n) {
                break;
            }
            int start = i;
            boolean digit = false;
            while (i < n && isWordChar(query, i)) {
                if (Character.isDigit(query.charAt(i))) {
                    digit = true;
                }
                i++;
            }
            String raw = query.substring(start, i);
            String norm = TextNormalizer.fold(raw);
            if (norm.isEmpty()) {
                continue;
            }
            boolean complete = i < n;
            out.add(new Token(raw, norm, start, i, complete, digit));
        }
        return out;
    }

    /** Folded word list, used when building statistics from clean text such as POI names. */
    public static List<String> words(String text) {
        List<Token> tokens = tokenize(text);
        List<String> out = new ArrayList<>(tokens.size());
        for (Token t : tokens) {
            out.add(t.norm());
        }
        return out;
    }

    private static boolean isWordChar(String s, int i) {
        char c = s.charAt(i);
        if (Character.isLetterOrDigit(c)) {
            return true;
        }
        int type = Character.getType(c);
        if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK) {
            return true;
        }
        return TextNormalizer.isApostrophe(c)
                && i > 0 && i + 1 < s.length()
                && Character.isLetter(s.charAt(i - 1)) && Character.isLetter(s.charAt(i + 1));
    }
}
