package com.mapsug.spell.text;

import java.util.ArrayList;
import java.util.List;

/** Optimal-string-alignment (restricted Damerau-Levenshtein) distance and alignment utilities. */
public final class EditDistance {

    private EditDistance() {
    }

    /** OSA distance, or {@code max + 1} as soon as it is known to exceed {@code max}. */
    public static int osa(CharSequence a, CharSequence b, int max) {
        int n = a.length();
        int m = b.length();
        if (Math.abs(n - m) > max) {
            return max + 1;
        }
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        int[] prev2 = new int[m + 1];
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            int rowMin = cur[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                char cb = b.charAt(j - 1);
                int cost = ca == cb ? 0 : 1;
                int v = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost);
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
                    v = Math.min(v, prev2[j - 2] + 1);
                }
                cur[j] = v;
                rowMin = Math.min(rowMin, v);
            }
            if (rowMin > max) {
                return max + 1;
            }
            int[] t = prev2;
            prev2 = prev;
            prev = cur;
            cur = t;
        }
        return Math.min(prev[m], max + 1);
    }

    public static int osa(CharSequence a, CharSequence b) {
        return osa(a, b, Integer.MAX_VALUE - 1);
    }

    /** Kinds of edit turning the intended token into the typed one. */
    public enum OpType { SUB, DEL, INS, TRANS }

    /**
     * One edit operation. {@code intended} / {@code typed} hold the characters involved: for SUB
     * one char each, for DEL the intended char that is missing, for INS the extra typed char
     * (plus the preceding intended char as context, or '^' at the start), for TRANS the two
     * intended chars in order.
     */
    public record Op(OpType type, String intended, String typed) {

        public String key() {
            return type.name().charAt(0) + ":" + intended + ">" + typed;
        }
    }

    /** Backtraces an OSA alignment and returns the edits that turn {@code intended} into {@code typed}. */
    public static List<Op> align(String typed, String intended) {
        int n = typed.length();
        int m = intended.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = typed.charAt(i - 1) == intended.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && typed.charAt(i - 1) == intended.charAt(j - 2)
                        && typed.charAt(i - 2) == intended.charAt(j - 1)) {
                    v = Math.min(v, d[i - 2][j - 2] + 1);
                }
                d[i][j] = v;
            }
        }
        List<Op> ops = new ArrayList<>();
        int i = n;
        int j = m;
        while (i > 0 || j > 0) {
            if (i > 1 && j > 1 && typed.charAt(i - 1) == intended.charAt(j - 2)
                    && typed.charAt(i - 2) == intended.charAt(j - 1)
                    && typed.charAt(i - 1) != typed.charAt(i - 2)
                    && d[i][j] == d[i - 2][j - 2] + 1) {
                ops.add(new Op(OpType.TRANS, intended.substring(j - 2, j), typed.substring(i - 2, i)));
                i -= 2;
                j -= 2;
            } else if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1]
                    + (typed.charAt(i - 1) == intended.charAt(j - 1) ? 0 : 1)) {
                if (typed.charAt(i - 1) != intended.charAt(j - 1)) {
                    ops.add(new Op(OpType.SUB, String.valueOf(intended.charAt(j - 1)),
                            String.valueOf(typed.charAt(i - 1))));
                }
                i--;
                j--;
            } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
                String ctx = j > 0 ? String.valueOf(intended.charAt(j - 1)) : "^";
                ops.add(new Op(OpType.INS, ctx, String.valueOf(typed.charAt(i - 1))));
                i--;
            } else {
                ops.add(new Op(OpType.DEL, String.valueOf(intended.charAt(j - 1)), ""));
                j--;
            }
        }
        return ops;
    }
}
