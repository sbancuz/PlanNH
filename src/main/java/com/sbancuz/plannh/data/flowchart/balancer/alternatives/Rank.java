package com.sbancuz.plannh.data.flowchart.balancer.alternatives;

import javax.annotation.Nullable;

import com.sbancuz.plannh.data.flowchart.balancer.Note;
import com.sbancuz.plannh.data.flowchart.balancer.SolverMessage;

/**
 * Why an answer is not the default. Everything past the gate count is a preference rather than
 * a fact - the sink-over-source tilt is a constant somebody chose, and "least material moved" is
 * a taste - so the reason travels with the option and gets shown, instead of quietly deciding on
 * the user's behalf. The strings and their ordering are part of the persisted choices format.
 */
public enum Rank {

    /** What the solver returned; nothing to say, so it carries no note key. */
    DEFAULT(null),
    /** Indistinguishable on every objective; only node ordering separated the two. */
    EQUALLY_VALID(SolverMessage.REASON_EQUALLY_VALID),
    /** Lost on the direction tilt: it imports where the default takes a surplus out. */
    IMPORTS_INSTEAD(SolverMessage.REASON_IMPORTS_INSTEAD),
    /**
     * Lost on stage 2: it leans on the outside more. Compared as fractions of a craft wasted,
     * which is a real comparison and still a debatable one - shown, not hidden, for that reason.
     */
    VOIDS_MORE(SolverMessage.REASON_VOIDS_MORE),
    /** Lost on stage 3: same gates and same excess, but more material moved internally. */
    MOVES_MORE(SolverMessage.REASON_MOVES_MORE),
    /**
     * Same gates and same excess, and it moves LESS material than the default. Reachable because
     * the default's tie enumeration is capped and a one-gate swap can step outside what it reached.
     */
    MOVES_LESS(SolverMessage.REASON_MOVES_LESS);

    /** The reason's note key; null only for {@link #DEFAULT}, which has nothing to say. */
    private final @Nullable SolverMessage msg;

    Rank(final @Nullable SolverMessage msg) {
        this.msg = msg;
    }

    public @Nullable Note toNote() {
        return msg == null ? null : msg.toNote();
    }
}
