package com.sbancuz.plannh.data.flowchart.balancer.stages;

import com.sbancuz.plannh.data.flowchart.balancer.Entry;
import com.sbancuz.plannh.data.flowchart.balancer.ModelBuilder;
import com.sbancuz.plannh.data.flowchart.balancer.Outcome;
import com.sbancuz.plannh.data.flowchart.balancer.SolveContext;
import com.sbancuz.plannh.data.flowchart.balancer.SolveResult;
import com.sbancuz.plannh.data.flowchart.balancer.Solver;
import com.sbancuz.plannh.data.flowchart.balancer.StageOutcome;

/**
 * The OUTPUT / INPUT balancer's entry: a single ungated MILP over the machine counts and the drawn
 * edge flows, assembled here from {@link ModelBuilder}'s parts (a DIFFERENT constraint family from
 * the AUTO conservation model: no gate binaries, the fold of the OUTPUT / INPUT balance). The
 * priority side is claimed EXACTLY - every unit it makes (OUTPUT) or consumes (INPUT) must be moved
 * on the drawn edges - and the other side runs at-least, its excess a bare "at least" slack.
 *
 * <p>
 * Imports are a last resort, never a cheaper machine: {@link Solver#extentFlow(SolveContext, boolean)}
 * first solves the no-import balance, and only when that is infeasible does it allow the at-least
 * rows to pull the residual from outside - a pin or machine floor the drawn edges cannot carry. A
 * chart the edges CAN carry never imports. The rescue's imports surface in the {@link SolutionView}
 * as boundary "imports" chips.
 *
 * <p>
 * The objective is the fewest machines - {@code min Σ count}, so the LP's decision variables ARE
 * the integer machine counts (a MILP, {@link ModelBuilder#extentCounts()}) and the answer needs no
 * read-out ceil: what the stage commits has whole-machine counts by construction. Every unpinned
 * machine carries the one-machine floor {@code count >= 1}. Pinned machines are locked at their
 * count (the FIXED_COUNT pin). A feasible chart gets one answer, so this entry commits the point
 * and short-circuits the chain.
 */
public final class ExtentMinStage implements Entry<StageOutcome> {

    /** False: outputs claimed exactly, inputs may over-supply. */
    private final boolean inputPriority;

    /**
     * @param inputPriority true for INPUT priority: inputs exact, outputs at least; false for OUTPUT.
     */
    public ExtentMinStage(final boolean inputPriority) {
        this.inputPriority = inputPriority;
    }

    @Override
    public String name() {
        return "extent minimum";
    }

    @Override
    public Outcome<StageOutcome> run(final SolveContext ctx) {
        final SolveResult result = Solver.extentFlow(ctx, inputPriority);
        if (result.isRejected()) {
            return new Outcome.Fail<>(result.rejection());
        }
        final StageOutcome point = result.point();
        ctx.commit(point);
        ctx.shortCircuit = true;
        return new Outcome.Continue<>(point);
    }
}
