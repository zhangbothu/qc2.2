package com.mapsug.spell.text;

/**
 * Language-aware phonetic key. Two tokens with the same key "sound alike" for a speaker of that
 * language, which catches errors edit distance ranks poorly: Spanish b/v, s/z/c, ll/y and the
 * silent h; English ph/f and ck/k; Indonesian/Malay pre-1972 spellings (oe/u, dj/j, tj/c).
 * After the language rules, vowels after the first letter are dropped and repeats collapsed.
 */
public final class Phonetic {

    private Phonetic() {
    }

    public static String key(String folded, Language lang) {
        if (folded.isEmpty()) {
            return folded;
        }
        String s = switch (lang) {
            case ES -> spanish(folded);
            case EN -> english(folded);
            case MS, ID -> malayIndonesian(folded);
        };
        return skeleton(s);
    }

    private static String spanish(String s) {
        s = s.replace("ll", "y")
                .replace("qu", "k")
                .replace("gue", "ge").replace("gui", "gi")
                .replace("ce", "se").replace("ci", "si")
                .replace("ch", "x")
                .replace('v', 'b').replace('z', 's').replace('w', 'u')
                .replace('c', 'k').replace('q', 'k');
        if (s.startsWith("h")) {
            s = s.substring(1);
        }
        return s.replace("h", "");
    }

    private static String english(String s) {
        s = s.replace("ph", "f").replace("ck", "k").replace("gh", "")
                .replace("ce", "se").replace("ci", "si").replace("cy", "sy")
                .replace("wr", "r").replace("kn", "n")
                .replace('c', 'k').replace('q', 'k').replace('z', 's')
                .replace("x", "ks");
        return s;
    }

    private static String malayIndonesian(String s) {
        return s.replace("oe", "u").replace("dj", "j").replace("tj", "c")
                .replace("sj", "sy").replace("nj", "ny").replace("ch", "c")
                .replace("kh", "k").replace('q', 'k').replace('v', 'f').replace('z', 's');
    }

    private static String skeleton(String s) {
        if (s.isEmpty()) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        sb.append(s.charAt(0));
        char last = s.charAt(0);
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == last) {
                continue;
            }
            last = c;
            if ("aeiouy".indexOf(c) >= 0) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
