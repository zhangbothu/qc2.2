package com.mapsug.spell.decode;

import com.mapsug.spell.dict.BigramModel;
import com.mapsug.spell.recall.Candidate;

import java.util.ArrayList;
import java.util.List;

/**
 * Query-level search over the per-token candidate lattice. A path scores
 * sum(token ranker scores) + contextWeight * sum(bigram gain between consecutive choices),
 * so "ecatepec de moaalos" prefers "morelos" because "de morelos" is a frequent place bigram,
 * and a real-word candidate can be rejected because it does not fit its neighbours.
 */
public final class BeamDecoder {

    private static final double MIN_GAIN = -3;
    private static final double MAX_GAIN = 6;

    public record Path(int[] choice, double score) {
    }

    private record State(int[] choice, double score, String lastWord) {
    }

    private final BigramModel bigram;

    public BeamDecoder(BigramModel bigram) {
        this.bigram = bigram;
    }

    public Path decode(List<List<Candidate>> lattice, double contextWeight, int beamWidth) {
        int n = lattice.size();
        List<State> beam = new ArrayList<>();
        beam.add(new State(new int[n], 0, null));
        for (int i = 0; i < n; i++) {
            List<Candidate> cands = lattice.get(i);
            List<State> next = new ArrayList<>(beam.size() * cands.size());
            for (State s : beam) {
                for (int k = 0; k < cands.size(); k++) {
                    Candidate c = cands.get(k);
                    double sc = s.score + c.score + contextWeight * transition(s.lastWord, c);
                    int[] choice = s.choice.clone();
                    choice[i] = k;
                    next.add(new State(choice, sc, c.lastWord()));
                }
            }
            next.sort((a, b) -> Double.compare(b.score, a.score));
            beam = next.size() > beamWidth ? new ArrayList<>(next.subList(0, beamWidth)) : next;
        }
        State best = beam.get(0);
        return new Path(best.choice, best.score);
    }

    /** Score of a fixed path, used for the all-original baseline. */
    public double score(List<List<Candidate>> lattice, int[] choice, double contextWeight) {
        double s = 0;
        String prev = null;
        for (int i = 0; i < lattice.size(); i++) {
            Candidate c = lattice.get(i).get(choice[i]);
            s += c.score + contextWeight * transition(prev, c);
            prev = c.lastWord();
        }
        return s;
    }

    private double transition(String prev, Candidate c) {
        double g = 0;
        if (prev != null) {
            g += clamp(bigram.gain(prev, c.firstWord(), c.prefix && !c.split));
        }
        if (c.split) {
            g += clamp(bigram.gain(c.firstWord(), c.lastWord(), c.prefix));
        }
        return g;
    }

    private static double clamp(double g) {
        return Math.max(MIN_GAIN, Math.min(MAX_GAIN, g));
    }
}
