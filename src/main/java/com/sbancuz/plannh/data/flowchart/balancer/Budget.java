package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.function.BooleanSupplier;

/**
 * A wall-clock ceiling shared by every model in one solve, so separate entry points can budget
 * independently. Passed around rather than held statically; optional stages skip themselves once it
 * is spent, and the non-optional ones get a workable slice even if that overshoots.
 *
 * <p>
 * Also the run's stop signal: a spent budget and a chart that has moved on both mean the answer
 * is unwanted, which is how a background solve gives up when the user edits underneath it.
 */
public final class Budget {

    private final long deadlineMillis;
    private final BooleanSupplier cancelled;

    private Budget(final long deadlineMillis, final BooleanSupplier cancelled) {
        this.deadlineMillis = deadlineMillis;
        this.cancelled = cancelled;
    }

    /** A budget that expires {@code millis} from now. */
    public static Budget of(final long millis) {
        return new Budget(System.currentTimeMillis() + millis, () -> false);
    }

    /** As above, but which also reports spent once {@code cancelled} says so. */
    public static Budget of(final long millis, final BooleanSupplier cancelled) {
        return new Budget(System.currentTimeMillis() + millis, cancelled);
    }

    /** A budget of {@code millis} that gives up for the same reasons this one does. */
    public Budget split(final long millis) {
        return new Budget(System.currentTimeMillis() + millis, cancelled);
    }

    public long remaining() {
        return Math.max(0, deadlineMillis - System.currentTimeMillis());
    }

    /** Whether this run should stop: out of time, or answering a chart that has moved on. */
    public boolean expired() {
        return remaining() <= 0 || cancelled.getAsBoolean();
    }
}
