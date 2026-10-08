package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.Set;

/**
 * One stage's solved point, captured so the next stage can build on it without the model that
 * produced it. A witness is only created when a stage genuinely owns that model's arrays - the
 * build-once arrays never move once handed off.
 */
public final class StageOutcome {

    public final double[] extents;
    public final double[] flows;
    public final double[] externals;
    /** Flow-derived gate support (which gates actually carry flow). */
    public final Set<Integer> support;
    /** Stage-2 objective: external quantity measured in crafts. */
    public final double externalQuantity;
    public final double internalFlow;
    public final boolean provenOptimal;

    private StageOutcome(final double[] extents, final double[] flows, final double[] externals,
        final Set<Integer> support, final double externalQuantity, final double internalFlow,
        final boolean provenOptimal) {
        this.extents = extents;
        this.flows = flows;
        this.externals = externals;
        this.support = support;
        this.externalQuantity = externalQuantity;
        this.internalFlow = internalFlow;
        this.provenOptimal = provenOptimal;
    }

    /** The point a fresh solve holds, with all derived metrics computed against {@code ctx}. */
    static StageOutcome of(final SolveContext ctx, final double[] extents, final double[] flows,
        final double[] externals, final boolean provenOptimal) {
        double qty = 0;
        for (int p = 0; p < externals.length; p++) {
            qty += externals[p] * ctx.externalWeight(p);
        }
        double flowSum = 0;
        for (final double f : flows) {
            flowSum += f;
        }
        return new StageOutcome(extents, flows, externals, ctx.gateSupport(externals), qty, flowSum, provenOptimal);
    }

    /**
     * The context's committed point, defensively copied for the profiler: a clone of the arrays and
     * the metrics recomputed against {@code ctx}. Only ever called when a profiler is attached
     * ({@link Profiler#enabled()}), so the cold path never pays for the copy.
     */
    static StageOutcome snapshot(final SolveContext ctx) {
        final StageOutcome point = ctx.point();
        return StageOutcome
            .of(ctx, point.extents.clone(), point.flows.clone(), point.externals.clone(), point.provenOptimal);
    }
}
