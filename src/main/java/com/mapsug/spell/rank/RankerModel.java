package com.mapsug.spell.rank;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Log-linear token scorer plus the two decoder knobs:
 * {@code contextWeight} (weight of the bigram gain between neighbouring choices) and
 * {@code acceptThreshold} (how much better than the untouched query a correction must score).
 */
public final class RankerModel {

    public final double[] weights;
    public double contextWeight;
    public double acceptThreshold;

    public RankerModel(double[] weights, double contextWeight, double acceptThreshold) {
        if (weights.length != FeatureExtractor.SIZE) {
            throw new IllegalArgumentException("expected " + FeatureExtractor.SIZE + " weights");
        }
        this.weights = weights;
        this.contextWeight = contextWeight;
        this.acceptThreshold = acceptThreshold;
    }

    /** Hand-set starting point, used before a model is trained. */
    public static RankerModel defaults() {
        return new RankerModel(new double[]{
                0.0,   // is_original
                -1.5,  // edit_distance
                -2.0,  // relative_distance
                0.3,   // channel_logprob
                0.5,   // log_freq
                0.4,   // log_freq_gain
                1.0,   // known_log_count
                0.6,   // phonetic_match
                0.8,   // first_char_match
                -0.8,  // length_diff
                -1.0,  // is_split
                -2.0,  // original_oov
                0.0,   // prefix_candidate
        }, 1.0, 1.5);
    }

    public RankerModel copy() {
        return new RankerModel(weights.clone(), contextWeight, acceptThreshold);
    }

    public double score(double[] f) {
        double s = 0;
        for (int i = 0; i < f.length; i++) {
            s += weights[i] * f[i];
        }
        return s;
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Properties p = new Properties();
        for (int i = 0; i < weights.length; i++) {
            p.setProperty("w." + FeatureExtractor.NAMES[i], Double.toString(weights[i]));
        }
        p.setProperty("context_weight", Double.toString(contextWeight));
        p.setProperty("accept_threshold", Double.toString(acceptThreshold));
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            p.store(w, "Map Sug spelling ranker");
        }
    }

    public static RankerModel load(Path file) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        RankerModel d = defaults();
        double[] w = d.weights.clone();
        for (int i = 0; i < w.length; i++) {
            String v = p.getProperty("w." + FeatureExtractor.NAMES[i]);
            if (v != null) {
                w[i] = Double.parseDouble(v);
            }
        }
        return new RankerModel(w,
                Double.parseDouble(p.getProperty("context_weight", Double.toString(d.contextWeight))),
                Double.parseDouble(p.getProperty("accept_threshold", Double.toString(d.acceptThreshold))));
    }

    public static RankerModel loadOrDefault(Path file) {
        if (file != null && Files.exists(file)) {
            try {
                return load(file);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        return defaults();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < weights.length; i++) {
            sb.append(String.format("%s=%.3f ", FeatureExtractor.NAMES[i], weights[i]));
        }
        sb.append(String.format("| context_weight=%.2f accept_threshold=%.2f", contextWeight, acceptThreshold));
        return sb.toString();
    }
}
