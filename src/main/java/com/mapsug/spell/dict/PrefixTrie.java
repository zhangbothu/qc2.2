package com.mapsug.spell.dict;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Character trie over folded tokens. Every node keeps the summed frequency of its subtree
 * ("how many queries continue this prefix") and the most frequent word below it, which makes
 * two Sug-specific operations cheap:
 * <ul>
 *   <li>{@link #prefixFreq}: is an unfinished token a legitimate prefix?</li>
 *   <li>{@link #fuzzyPrefixSearch}: which trie paths are within k edits of the typed prefix?
 *       This is a DFS that carries one OSA dynamic-programming row per depth and prunes as soon
 *       as the row minimum exceeds k, so cost depends on the neighbourhood size, not on the
 *       vocabulary size.</li>
 * </ul>
 */
public final class PrefixTrie {

    static final class Node {
        char[] keys = new char[0];
        Node[] kids = new Node[0];
        long subtreeFreq;
        long bestFreq = -1;
        int bestWord = -1;
        int word = -1;

        Node child(char c) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i] == c) {
                    return kids[i];
                }
            }
            return null;
        }

        Node addChild(char c) {
            Node existing = child(c);
            if (existing != null) {
                return existing;
            }
            keys = Arrays.copyOf(keys, keys.length + 1);
            kids = Arrays.copyOf(kids, kids.length + 1);
            Node n = new Node();
            keys[keys.length - 1] = c;
            kids[kids.length - 1] = n;
            return n;
        }
    }

    /** A trie path within edit distance {@code distance} of the query. */
    public record Match(String path, int distance, long subtreeFreq, int bestWord, int exactWord) {
    }

    private final Node root = new Node();

    public void insert(String key, int wordId, long freq) {
        Node n = root;
        touch(n, wordId, freq);
        for (int i = 0; i < key.length(); i++) {
            n = n.addChild(key.charAt(i));
            touch(n, wordId, freq);
        }
        n.word = wordId;
    }

    private static void touch(Node n, int wordId, long freq) {
        n.subtreeFreq += freq;
        if (freq > n.bestFreq) {
            n.bestFreq = freq;
            n.bestWord = wordId;
        }
    }

    private Node find(String prefix) {
        Node n = root;
        for (int i = 0; i < prefix.length() && n != null; i++) {
            n = n.child(prefix.charAt(i));
        }
        return n;
    }

    public long prefixFreq(String prefix) {
        Node n = find(prefix);
        return n == null ? 0 : n.subtreeFreq;
    }

    /** Most frequent word id under {@code prefix}, or -1. */
    public int bestWordWithPrefix(String prefix) {
        Node n = find(prefix);
        return n == null ? -1 : n.bestWord;
    }

    /**
     * Returns trie paths p with minDepth <= |p| <= maxDepth and OSA(input, p) <= maxDist, best
     * first (distance ascending, subtree frequency descending), at most {@code limit} entries.
     */
    public List<Match> fuzzyPrefixSearch(String input, int maxDist, int minDepth, int maxDepth, int limit) {
        int n = input.length();
        maxDepth = Math.max(0, maxDepth);
        int[][] rows = new int[maxDepth + 1][n + 1];
        for (int j = 0; j <= n; j++) {
            rows[0][j] = j;
        }
        char[] in = input.toCharArray();
        char[] path = new char[maxDepth + 1];
        List<Match> out = new ArrayList<>();
        dfs(root, 0, in, path, rows, maxDist, minDepth, maxDepth, out);
        out.sort((a, b) -> a.distance != b.distance ? Integer.compare(a.distance, b.distance)
                : Long.compare(b.subtreeFreq, a.subtreeFreq));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    private static void dfs(Node node, int depth, char[] in, char[] path, int[][] rows, int maxDist,
                            int minDepth, int maxDepth, List<Match> out) {
        if (depth >= maxDepth) {
            return;
        }
        int n = in.length;
        int i = depth + 1;
        int[] prev = rows[i - 1];
        int[] row = rows[i];
        for (int k = 0; k < node.keys.length; k++) {
            char c = node.keys[k];
            Node kid = node.kids[k];
            path[depth] = c;
            row[0] = i;
            int min = row[0];
            for (int j = 1; j <= n; j++) {
                int cost = in[j - 1] == c ? 0 : 1;
                int v = Math.min(Math.min(prev[j] + 1, row[j - 1] + 1), prev[j - 1] + cost);
                if (i > 1 && j > 1 && in[j - 1] == path[i - 2] && in[j - 2] == c) {
                    v = Math.min(v, rows[i - 2][j - 2] + 1);
                }
                row[j] = v;
                if (v < min) {
                    min = v;
                }
            }
            if (i >= minDepth && row[n] <= maxDist) {
                out.add(new Match(new String(path, 0, i), row[n], kid.subtreeFreq, kid.bestWord, kid.word));
            }
            if (min <= maxDist) {
                dfs(kid, i, in, path, rows, maxDist, minDepth, maxDepth, out);
            }
        }
    }
}
