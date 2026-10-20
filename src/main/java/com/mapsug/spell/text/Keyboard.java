package com.mapsug.spell.text;

/**
 * QWERTY adjacency. All four target markets (MX, GB, MY, ID) predominantly use QWERTY-family
 * layouts on mobile, so a single table is enough; adjacency is used as a prior for unseen
 * substitutions in the error model.
 */
public final class Keyboard {

    private static final String[] ROWS = {"1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm"};
    private static final int[] OFFSETS = {0, 0, 1, 2};
    private static final boolean[][] ADJ = new boolean[128][128];

    static {
        for (int r = 0; r < ROWS.length; r++) {
            for (int c = 0; c < ROWS[r].length(); c++) {
                char a = ROWS[r].charAt(c);
                double xa = c + OFFSETS[r] * 0.5;
                for (int r2 = Math.max(0, r - 1); r2 <= Math.min(ROWS.length - 1, r + 1); r2++) {
                    for (int c2 = 0; c2 < ROWS[r2].length(); c2++) {
                        char b = ROWS[r2].charAt(c2);
                        double xb = c2 + OFFSETS[r2] * 0.5;
                        if (a != b && Math.abs(xa - xb) <= 1.0) {
                            ADJ[a][b] = true;
                        }
                    }
                }
            }
        }
    }

    private Keyboard() {
    }

    public static boolean adjacent(char a, char b) {
        return a < 128 && b < 128 && ADJ[a][b];
    }
}
