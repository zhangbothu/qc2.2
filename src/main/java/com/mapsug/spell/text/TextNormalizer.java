package com.mapsug.spell.text;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Folds surface text into the matching key used by every index: lower case, diacritics removed,
 * apostrophes dropped. "Juárez" and "juarez" share one key, "Sainsbury's" becomes "sainsburys".
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String fold(String s) {
        if (s.isEmpty()) {
            return s;
        }
        boolean ascii = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x80 || c == '\'' || (c >= 'A' && c <= 'Z')) {
                ascii = false;
                break;
            }
        }
        if (ascii) {
            return s;
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKD);
        StringBuilder sb = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            int type = Character.getType(c);
            if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }
            if (isApostrophe(c)) {
                continue;
            }
            switch (c) {
                case 'ß' -> sb.append("ss");
                case 'ø', 'Ø' -> sb.append('o');
                case 'ł', 'Ł' -> sb.append('l');
                case 'đ', 'Đ' -> sb.append('d');
                case 'æ', 'Æ' -> sb.append("ae");
                default -> sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    public static boolean isApostrophe(char c) {
        return c == '\'' || c == '\u2019' || c == '\u2018' || c == '`';
    }

    /**
     * Returns the shortest prefix of {@code display} whose folded form has {@code foldedLen}
     * characters, so that a prefix chosen on folded keys can be shown with its original accents.
     */
    public static String prefixByFoldedLength(String display, int foldedLen) {
        if (foldedLen <= 0) {
            return "";
        }
        int acc = 0;
        for (int i = 0; i < display.length(); i++) {
            acc += fold(String.valueOf(display.charAt(i))).length();
            if (acc >= foldedLen) {
                return display.substring(0, i + 1);
            }
        }
        return display;
    }

    /** Re-applies the casing style of the user's token (UPPER, Title, lower) to a replacement. */
    public static String applyCase(String original, String replacement) {
        if (replacement.isEmpty()) {
            return replacement;
        }
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < original.length(); i++) {
            char c = original.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        if (letters > 1 && upper == letters) {
            return replacement.toUpperCase(Locale.ROOT);
        }
        if (!original.isEmpty() && Character.isUpperCase(original.charAt(0))) {
            return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
        }
        return replacement;
    }
}
