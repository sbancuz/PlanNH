package com.sbancuz.plannh.data.flowchart.balancer.stages;

import java.util.Set;

import com.sbancuz.plannh.data.flowchart.balancer.Prelude;
import com.sbancuz.plannh.data.flowchart.balancer.SolveContext;
import com.sbancuz.plannh.data.flowchart.balancer.SolveResult;
import com.sbancuz.plannh.data.flowchart.balancer.Solver;

/**
 * The zero-gate LP fast path, as a {@link Prelude}: if the chart balances with every gate closed
 * (the flow stage over the empty support and a zero quantity cap), the gate count and external
 * quantity are trivially optimal - 0 gates and 0 external quantity - so this single LP IS the whole
 * solve. Committing the canonical point short-circuits the chain so the entry and stages never run.
 */
public final class FastPathStage implements Prelude {

    @Override
    public String name() {
        return "fast path";
    }

    @Override
    public void run(final SolveContext ctx) {
        final SolveResult fast = Solver.flowMinimal(ctx, Set.of(), 0.0);
        if (fast.isRejected()) {
            return; // does not balance gate-free; the rest of the chain runs
        }
        ctx.commit(Solver.canonicalize(ctx, Set.of(), fast.point()));
        ctx.shortCircuit = true;
    }
}
