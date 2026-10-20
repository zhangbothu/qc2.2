package com.mapsug.spell.data;

import com.mapsug.spell.text.EditDistance;
import com.mapsug.spell.text.Token;
import com.mapsug.spell.text.Tokenizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Aligns a raw query with its Google Maps Top1 result and extracts token-level corrections
 * (the same idea as the script that produced the triple table). Complete tokens are matched
 * against whole result words; the unfinished last token is matched against result-word prefixes
 * and its correction is length-aligned ("aple" -> "appl", "coefe" -> "coffe") so that the
 * reference never completes the user's prefix.
 *
 * <p>Used to build query-level references for evaluation and to produce triples from sample data.
 */
public final class TripleExtractor {

    public record Fix(int tokenIndex, String wrong, String right, int distance, boolean complete) {
    }

    private TripleExtractor() {
    }

    public static int maxDistance(int len) {
        if (len <= 2) {
            return 0;
        }
        if (len <= 4) {
            return 1;
        }
        return len <= 8 ? 2 : 3;
    }

    public static List<Fix> extract(String query, String top1Name, String top1Address) {
        List<Token> tokens = Tokenizer.tokenize(query);
        List<String> targets = new ArrayList<>(Tokenizer.words(top1Name));
        targets.addAll(Tokenizer.words(top1Address == null ? "" : top1Address));
        Set<String> targetSet = new HashSet<>(targets);
        List<Fix> fixes = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            String q = t.norm();
            int max = maxDistance(q.length());
            if (t.hasDigit() || max == 0) {
                continue;
            }
            if (t.complete()) {
                if (targetSet.contains(q)) {
                    continue;
                }
                String best = null;
                int bestD = max + 1;
                for (String w : targets) {
                    int d = EditDistance.osa(q, w, max);
                    if (d < bestD) {
                        bestD = d;
                        best = w;
                    }
                }
                if (best != null && bestD <= max) {
                    fixes.add(new Fix(i, q, best, bestD, true));
                }
            } else {
                boolean ok = false;
                for (String w : targets) {
                    if (w.startsWith(q)) {
                        ok = true;
                        break;
                    }
                }
                if (ok) {
                    continue;
                }
                String best = null;
                int bestD = max + 1;
                for (String w : targets) {
                    int n = q.length();
                    for (int len = Math.max(1, n - max); len <= Math.min(w.length(), n + max); len++) {
                        int d = EditDistance.osa(q, w.substring(0, len), max);
                        if (d < bestD) {
                            bestD = d;
                            best = w.substring(0, Math.min(n, w.length()));
                        }
                    }
                }
                if (best != null && bestD <= max && !best.equals(q)) {
                    fixes.add(new Fix(i, q, best, bestD, false));
                }
            }
        }
        return fixes;
    }

    /**
     * Share of the query's alphabetic tokens (3+ chars) that match the Top1 text exactly, as a
     * prefix, or through a fix. A low value means the Top1 is probably a different place, so
     * the pair is not a reliable reference.
     */
    public static double alignedFraction(String query, String top1Name, String top1Address, List<Fix> fixes) {
        List<Token> tokens = Tokenizer.tokenize(query);
        List<String> targets = new ArrayList<>(Tokenizer.words(top1Name));
        targets.addAll(Tokenizer.words(top1Address == null ? "" : top1Address));
        Set<Integer> fixed = new HashSet<>();
        for (Fix f : fixes) {
            fixed.add(f.tokenIndex());
        }
        int considered = 0;
        int aligned = 0;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.hasDigit() || t.length() < 3) {
                continue;
            }
            considered++;
            if (fixed.contains(i)) {
                aligned++;
                continue;
            }
            for (String w : targets) {
                if (w.equals(t.norm()) || (!t.complete() && w.startsWith(t.norm()))) {
                    aligned++;
                    break;
                }
            }
        }
        return considered == 0 ? 1 : (double) aligned / considered;
    }

    /** Folded reference query: the raw query with every extracted fix applied. */
    public static String referenceQuery(String query, List<Fix> fixes) {
        List<Token> tokens = Tokenizer.tokenize(query);
        String[] words = new String[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            words[i] = tokens.get(i).norm();
        }
        for (Fix f : fixes) {
            words[f.tokenIndex()] = f.right();
        }
        return String.join(" ", words);
    }
}
