package com.sbancuz.plannh.data.flowchart.balancer.stages;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sbancuz.plannh.data.flowchart.balancer.Entry;
import com.sbancuz.plannh.data.flowchart.balancer.Outcome;
import com.sbancuz.plannh.data.flowchart.balancer.SolveContext;
import com.sbancuz.plannh.data.flowchart.balancer.SolveResult;
import com.sbancuz.plannh.data.flowchart.balancer.Solver;
import com.sbancuz.plannh.data.flowchart.balancer.SolverMessage;
import com.sbancuz.plannh.data.flowchart.balancer.StageOutcome;

/**
 * The AUTO chain's entry: gate count. Decides WHICH ingredient gates open and proves the count is
 * optimal. It runs twice - an LP deletion filter always produces a minimal support fast, then a
 * short exact-MILP slice certifies or beats it when the chart is small enough for branch-and-bound;
 * large charts keep the filter answer, uncertified. Hands the carrying set ({@link GatePlan}) and
 * its weighted cost as the cap the next stage searches under.
 *
 * <p>
 * This stage owns the AUTO non-linear branches (filter vs certified MILP); the chain stays flat.
 */
public final class GateCountStage implements Entry<GateCountStage.GatePlan> {

    @Override
    public String name() {
        return "gate count";
    }

    @Override
    public Outcome<GatePlan> run(final SolveContext ctx) {
        // Deletion filter: an LP over the whole gate structure, raw-quantity objective, then
        // dropping gates one by one while feasibility holds -> minimal support fast, no big-M.
        final SolveResult filter = deletionFilter(ctx);
        if (filter.isRejected()) {
            return new Outcome.Fail<>(filter.rejection());
        }
        final StageOutcome filterPoint = filter.point();
        // Everything with a binary in it is sized from the filter's own solution: it is the first
        // point that exists, and every later stage lives at the same scale.
        final double scale = ctx.solutionScale(filterPoint.extents, filterPoint.flows, filterPoint.externals);

        // Certification is optional: the filter's support is already minimal, and proving it so is
        // the first thing to drop when the whole solve's budget is gone.
        Set<Integer> s1Support = filterPoint.support;
        StageOutcome s1Witness = filterPoint;
        boolean certified = false;
        if (!ctx.budget.expired()) {
            final SolveResult milp = Solver.gateMILP(ctx, ctx.weightedCost(filterPoint.support), scale);
            final StageOutcome milpPoint = milp.point();
            if (milpPoint != null
                && ctx.weightedCost(milpPoint.support) <= ctx.weightedCost(filterPoint.support) + 0.5) {
                s1Support = milpPoint.support;
                s1Witness = milpPoint;
                certified = milpPoint.provenOptimal;
            }
        }
        if (!certified) {
            // Legacy keeps this note so the user knows the count is bought, not proven.
            ctx.stageNotes.add(SolverMessage.GATE_COUNT_NOT_CERTIFIED.toNote(s1Support.size()));
        }

        // The cap must cover what stage 1 actually carried, not what its support reports: a gate
        // carrying flow too small to register still costs its weight, and capping below it leaves
        // stage 2 infeasible on a graph stage 1 has just solved.
        final Set<Integer> carrying = ctx.carryingGates(s1Witness.externals, s1Support);
        return new Outcome.Continue<>(new GatePlan(carrying, ctx.weightedCost(carrying), scale, certified));
    }

    /**
     * Gate-count LP fallback: a weighted-external-quantity LP opens a starting support, then a
     * deterministic deletion filter closes gates one by one (sources first, thinnest flow first)
     * while feasibility holds. The result is a MINIMAL support - no proper subset is feasible -
     * in a handful of fast LP solves and with no big-M anywhere.
     */
    private static SolveResult deletionFilter(final SolveContext ctx) {
        final SolveResult lp0 = Solver.externalsLp(ctx, null);
        if (lp0.isRejected()) return lp0;
        StageOutcome best = lp0.point();
        Set<Integer> support = best.support;

        final double[] gateFlow = new double[ctx.model.gates.size()];
        for (int p = 0; p < best.externals.length; p++) {
            gateFlow[ctx.model.portGate[p]] += best.externals[p];
        }
        final List<Integer> order = new ArrayList<>(support);
        order.sort(
            Comparator.<Integer, Double>comparing(g -> -ctx.importTilt(g))
                .thenComparing(g -> gateFlow[g])
                .thenComparing(g -> g));

        for (final int gate : order) {
            if (!support.contains(gate)) continue; // already dropped via a shrunken support
            final Set<Integer> trial = new HashSet<>(support);
            trial.remove(gate);
            final SolveResult solved = Solver.externalsLp(ctx, trial);
            if (!solved.isRejected()) {
                support = solved.point().support;
                best = solved.point();
            }
        }
        return SolveResult.solved(best);
    }

    /**
     * The gate-count entry's hand-off: the gates that must stay open, their weighted cost as the
     * cap stage 2 searches under, the scale every later binary is sized from, and whether the
     * count is proven optimal or bought with the deletion filter.
     */
    public static record GatePlan(Set<Integer> carrying, double weightedCap, double scale, boolean certified) {

        public GatePlan {
            carrying = Set.copyOf(carrying);
        }
    }
}
