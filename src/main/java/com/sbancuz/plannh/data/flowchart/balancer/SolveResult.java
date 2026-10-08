package com.sbancuz.plannh.data.flowchart.balancer;

import javax.annotation.Nullable;

/**
 * One solver call's outcome: a usable point, or the rejection note that says why there is none.
 * The note is never null - a rejection always has a reason - so a null {@code point} and the note
 * travel together and no reason hides in a side channel.
 *
 * @param point     the solved point, or null when the model was rejected
 * @param rejection why there is no point (a default note on success, mirroring the never-null
 *                  {@link SolveContext#rejection})
 */
public record SolveResult(@Nullable StageOutcome point, Note rejection) {

    /** A rejected solve: no point, and {@code rejection} says why. */
    public static SolveResult rejected(final Note rejection) {
        return new SolveResult(null, rejection);
    }

    /** A usable point; the rejection slot holds a harmless default. */
    public static SolveResult solved(final StageOutcome point) {
        return new SolveResult(point, SolverMessage.SOLVER_NO_SOLUTION.toNote());
    }

    /** Whether the solve produced no usable point. */
    public boolean isRejected() {
        return point == null;
    }
}
