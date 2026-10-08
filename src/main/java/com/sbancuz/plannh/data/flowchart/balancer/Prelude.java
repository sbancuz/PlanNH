package com.sbancuz.plannh.data.flowchart.balancer;

/**
 * A chain element that runs before the {@link Entry}: it may commit a point and short-circuit the
 * chain, and it carries nothing onward. The canonical example is the zero-gate fast path - a single
 * LP that, when it balances, IS the whole solve. A prelude that finds nothing for itself is not a
 * failure; the entry simply decides, so a prelude leaves the point where it found it.
 */
public interface Prelude extends ChainElement {

    /** Execute this prelude against the shared, zero-copy context. */
    void run(SolveContext ctx);
}
