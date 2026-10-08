package com.sbancuz.plannh.data.flowchart.balancer;

import javax.annotation.Nullable;

/**
 * The instrumentation hook for a solve. Everything a test wants to read that the UI does not -
 * per-stage wall time, model size and solver state, big-M growths, rejections, the pass-2 floor
 * replay, the fast path - flows through here instead of being carried on
 * {@link SolutionView}/{@link StageOutcome}. The result payload stays shaped by what the UI reads;
 * a test that needs more attaches a profiler at run time.
 *
 * <p>
 * A no-op by default: every method is a {@code default} empty implementation, and
 * {@link #disabled()} hands out one shared instance that costs nothing to call. The run only
 * pays for {@link SolutionView}/{@link StageOutcome} snapshot construction and
 * {@code System.currentTimeMillis()} when {@link #enabled()} says a profiler is attached - the
 * pipeline never allocates on the cold path just to satisfy a test it did not ask for.
 *
 * <p>
 * Data parity with the run payload: the run-level {@link #runFinished} event carries a
 * {@link SolutionView} and the per-stage {@link #stageFinished} carries a {@link StageOutcome}
 * snapshot - so a test can read the same data the GUI consumes plus the per-stage detail, without
 * those shapes leaking into the result payload.
 */
public interface Profiler {

    /** The whole solve is starting; {@code counts} are the model's build-once sizes. */
    default void runStarted(final BalanceMode mode, final int machines, final int ports, final int gates,
        final int edges) {}

    /** The whole solve finished, successfully or not; {@code failure} is null on success. */
    default void runFinished(@Nullable final SolutionView solution, final @Nullable String failure) {}

    /** A stage is about to solve. */
    default void stageStarted(final String stage) {}

    /** A stage solved to a usable point ({@code witness} non-empty) or failed/skipped (empty). */
    default void stageFinished(final String stage, final java.util.Optional<StageOutcome> witness,
        final long wallMillis) {}

    /** One model was solved: how long, what the solver said, and how big it was. */
    default void modelSolved(final String label, final long wallMillis, final String state, final int vars,
        final int exprs) {}

    /** A big-M gate link pressed its cap and was grown for the next solve attempt. */
    default void bigMGrew(final String label, final double bigM) {}

    /** A solve produced a point the independent validation rejected; {@code reason} is why. */
    default void pointRejected(final String label, final String reason) {}

    /** The pass-2 "every machine runs" replay ran, with how many machines were idle in pass 1. */
    default void floorReplay(final int idleBefore) {}

    /** The zero-gate fast path committed the point without stages 1-3. */
    default void fastPath() {}

    /**
     * Whether this profiler actually records anything. {@link #disabled()} returns false; every
     * attached profiler must return true so the pipeline knows to build the witness data.
     */
    boolean enabled();

    /** The one shared no-op instance; the cold path calls it and nothing happens. */
    static Profiler disabled() {
        return DISABLED;
    }

    Profiler DISABLED = new Profiler() {

        @Override
        public boolean enabled() {
            return false;
        }
    };
}
