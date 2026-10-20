package com.mapsug.spell.rank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Listwise (softmax cross-entropy) training of the log-linear ranker with AdaGrad and L2.
 * Each example is the candidate list of one token with the index of the correct candidate:
 * the right side of a triple for typos, the original for clean tokens.
 */
public final class RankerTrainer {

    public record Example(double[][] features, int label, double weight) {
    }

    private final int epochs;
    private final double learningRate;
    private final double l2;
    private final long seed;

    public RankerTrainer(int epochs, double learningRate, double l2, long seed) {
        this.epochs = epochs;
        this.learningRate = learningRate;
        this.l2 = l2;
        this.seed = seed;
    }

    public double[] train(List<Example> examples, double[] init) {
        double[] w = init.clone();
        double[] g2 = new double[w.length];
        List<Example> order = new ArrayList<>(examples);
        Random rnd = new Random(seed);
        double[] grad = new double[w.length];
        for (int epoch = 0; epoch < epochs; epoch++) {
            Collections.shuffle(order, rnd);
            for (Example ex : order) {
                int k = ex.features.length;
                double[] s = new double[k];
                double max = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < k; i++) {
                    s[i] = dot(w, ex.features[i]);
                    max = Math.max(max, s[i]);
                }
                double z = 0;
                for (int i = 0; i < k; i++) {
                    s[i] = Math.exp(s[i] - max);
                    z += s[i];
                }
                java.util.Arrays.fill(grad, 0);
                for (int i = 0; i < k; i++) {
                    double p = s[i] / z - (i == ex.label ? 1 : 0);
                    if (p == 0) {
                        continue;
                    }
                    for (int j = 0; j < w.length; j++) {
                        grad[j] += ex.weight * p * ex.features[i][j];
                    }
                }
                for (int j = 0; j < w.length; j++) {
                    double g = grad[j] + l2 * w[j];
                    g2[j] += g * g;
                    w[j] -= learningRate * g / (Math.sqrt(g2[j]) + 1e-8);
                }
            }
        }
        return w;
    }

    /** Share of examples whose correct candidate is ranked first. */
    public static double top1Accuracy(List<Example> examples, double[] w) {
        if (examples.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (Example ex : examples) {
            int best = 0;
            double bs = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < ex.features.length; i++) {
                double s = dot(w, ex.features[i]);
                if (s > bs) {
                    bs = s;
                    best = i;
                }
            }
            if (best == ex.label) {
                ok++;
            }
        }
        return (double) ok / examples.size();
    }

    private static double dot(double[] w, double[] f) {
        double s = 0;
        for (int i = 0; i < f.length; i++) {
            s += w[i] * f[i];
        }
        return s;
    }
}
