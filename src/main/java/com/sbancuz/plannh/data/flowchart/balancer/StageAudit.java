package com.sbancuz.plannh.data.flowchart.balancer;

/**
 * The between-pass floor gate: consulted by {@link Chain#run(SolveContext)} after a full pass,
 * with the finished pass's point still on the context, and handed the run's second pass. Returns
 * the {@code floors} that force a re-run, or an empty array when pass 1 left nothing to fix.
 * The canonical example is "every machine runs": after the chain solved once, it looks for machines
 * left idle and, when it finds any, floors every pinned-component machine so a second full pass
 * forces them on.
 */
@FunctionalInterface
public interface StageAudit {

    /** Floors for the second pass, or an empty array when pass 1 left nothing to fix. */
    double[] floorsFor(SolveContext ctx);
}
