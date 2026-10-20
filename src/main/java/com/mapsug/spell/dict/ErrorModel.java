package com.mapsug.spell.dict;

import com.mapsug.spell.text.EditDistance;
import com.mapsug.spell.text.Keyboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Noisy-channel error model log P(typed | intended), learned from the triples by aligning each
 * (wrong, right) pair and counting the character edits. Unseen substitutions between adjacent
 * keys and doubled letters get a larger pseudo-count, so the model generalises to typos that are
 * absent from the training triples.
 */
public final class ErrorModel {

    private final Map<String, Long> opCounts = new HashMap<>();
    private long charCount;
    private long opTotal;
    private final double alpha;

    public ErrorModel(double alpha) {
        this.alpha = alpha;
    }

    public void observe(String wrong, String right, long weight) {
        for (EditDistance.Op op : EditDistance.align(wrong, right)) {
            opCounts.merge(op.key(), weight, Long::sum);
            opTotal += weight;
        }
        charCount += (long) right.length() * weight;
    }

    private double prior(EditDistance.Op op) {
        return switch (op.type()) {
            case SUB -> Keyboard.adjacent(op.intended().charAt(0), op.typed().charAt(0)) ? 4 * alpha : alpha;
            case INS -> op.intended().equals(op.typed()) || Keyboard.adjacent(op.intended().charAt(0), op.typed().charAt(0))
                    ? 4 * alpha : alpha;
            case DEL -> 2 * alpha;
            case TRANS -> 3 * alpha;
        };
    }

    public double logProbOp(EditDistance.Op op) {
        long c = opCounts.getOrDefault(op.key(), 0L);
        double denom = Math.max(1, charCount) + 40 * alpha;
        return Math.log((c + prior(op)) / denom);
    }

    /** Sum of log-probabilities of the edits needed to turn {@code intended} into {@code typed}. */
    public double logChannel(String typed, String intended) {
        if (typed.equals(intended)) {
            return 0;
        }
        List<EditDistance.Op> ops = EditDistance.align(typed, intended);
        double s = 0;
        for (EditDistance.Op op : ops) {
            s += logProbOp(op);
        }
        return s;
    }

    public long observedOps() {
        return opTotal;
    }

    public int distinctOps() {
        return opCounts.size();
    }
}
