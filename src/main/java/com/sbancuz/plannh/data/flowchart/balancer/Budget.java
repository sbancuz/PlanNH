package com.sbancuz.plannh.data.flowchart.balancer;

/**
 * A wall-clock ceiling shared by every model in one solve, so separate entry points can budget
 * independently. Passed around rather than held statically; optional stages skip themselves once it
 * is spent, and the non-optional ones get a workable slice even if that overshoots.
 */
public final class Budget {

    private final long deadlineMillis;

    private Budget(final long deadlineMillis) {
        this.deadlineMillis = deadlineMillis;
    }

    /** A budget that expires {@code millis} from now. */
    public static Budget of(final long millis) {
        return new Budget(System.currentTimeMillis() + millis);
    }

    public long remaining() {
        return Math.max(0, deadlineMillis - System.currentTimeMillis());
    }

    public boolean expired() {
        return remaining() <= 0;
    }
}
