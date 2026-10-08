package com.sbancuz.plannh.data.flowchart.balancer.stages;

import com.sbancuz.plannh.data.flowchart.balancer.SolveContext;
import com.sbancuz.plannh.data.flowchart.balancer.StageAudit;

/**
 * "Every machine runs": after the chain solved once, floor every pinned-connected unpinned machine
 * at pass 1's smallest running rate so a second pass forces them on. Implemented as a
 * {@link StageAudit} - the chain consults this AFTER a full pass and re-runs the chain under the
 * floors when any machine was left idle. A fixed floor can exceed a machine's natural rate and
 * conjure phantom externals, so it is only ever a floor derived from a rate this chart already
 * achieves.
 */
public final class EveryMachineRuns implements StageAudit {

    @Override
    public double[] floorsFor(final SolveContext ctx) {
        return ctx.machineRunFloors(ctx.extents());
    }
}
