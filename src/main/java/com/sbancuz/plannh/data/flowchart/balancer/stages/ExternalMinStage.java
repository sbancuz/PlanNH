package com.sbancuz.plannh.data.flowchart.balancer.stages;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.sbancuz.plannh.data.flowchart.balancer.Outcome;
import com.sbancuz.plannh.data.flowchart.balancer.SolveContext;
import com.sbancuz.plannh.data.flowchart.balancer.SolveResult;
import com.sbancuz.plannh.data.flowchart.balancer.Solver;
import com.sbancuz.plannh.data.flowchart.balancer.Stage;
import com.sbancuz.plannh.data.flowchart.balancer.StageOutcome;

/**
 * External quantity: the LEAST the graph leans on the outside, subject to the gate count's cap.
 * Two shapes - the certified path searches over the binaries under the weighted-gate-count cap
 * ({@link Solver#quantityMILP}), the filter path fixes the gates to the {@link GateCountStage.GatePlan}'s carrying
 * set and solves a plain LP ({@link Solver#fixedQuantity}). On a certified failure it falls back to
 * the fixed LP: that is the point the gate count already satisfies, so it cannot be infeasible,
 * though its quantity is never better than the free search would have found. Also surfaces the tied
 * supports ({@link #tiedSupports}) the internal-flow stage gets to choose between, and the stage-2
 * quantity cap ({@code qtyCap}) every candidate is held to.
 */
public final class ExternalMinStage implements Stage<GateCountStage.GatePlan, ExternalMinStage.ExternalPlan> {

    @Override
    public String name() {
        return "external quantity";
    }

    @Override
    public Outcome<ExternalPlan> run(final SolveContext ctx, final GateCountStage.GatePlan plan) {
        boolean certified = plan.certified();
        SolveResult s2 = certified ? Solver.quantityMILP(ctx, plan.weightedCap(), List.of(), plan.scale())
            : Solver.fixedQuantity(ctx, plan.carrying());
        if (s2.isRejected() && certified) {
            // The free search over minimal-count supports did not close. Holding the gates to the
            // ones the gate count used makes this an LP that the gate-count solution already
            // satisfies, so it cannot be infeasible; the quantity it finds is never better than the
            // free search would have found, and the tie enumeration below is deliberately skipped.
            certified = false;
            s2 = Solver.fixedQuantity(ctx, plan.carrying());
        }
        if (s2.isRejected()) {
            return new Outcome.Fail<>(s2.rejection());
        }
        final StageOutcome s2Point = s2.point();

        // Ties at (count, quantity) are resolved by the next stage's objective: re-run it for each
        // tied stage-2 support and keep the least internal flow. Only meaningful on the certified
        // path (the filter path has no optimality frontier to enumerate). Each candidate opens the
        // gates ITS OWN witness carries - feeding s2's externals to another candidate's support
        // would union the two and make every later candidate a relaxation of the first.
        final StageOutcome s1Fixed = certified ? Solver.fixedQuantity(ctx, plan.carrying())
            .point() : null;
        final List<StageOutcome> candidates = certified && !ctx.budget.expired()
            ? tiedSupports(ctx, s2Point, s1Fixed, plan)
            : List.of(s2Point);

        return new Outcome.Continue<>(
            new ExternalPlan(
                s1Fixed == null ? Optional.empty() : Optional.of(s1Fixed),
                candidates,
                s2Point.externalQuantity));
    }

    /**
     * The stage-2 optimum plus any other witness tied with it at the same (weighted gate count,
     * external quantity), grown by no-good cuts on tried supports. Each is returned whole; a
     * support without the externals that produced it cannot say which of its gates actually carry
     * flow.
     */
    private static List<StageOutcome> tiedSupports(final SolveContext ctx, final StageOutcome s2,
        final StageOutcome s1Fixed, final GateCountStage.GatePlan plan) {
        final List<StageOutcome> candidates = new ArrayList<>();
        candidates.add(s2);
        final List<Set<Integer>> cuts = new ArrayList<>();
        cuts.add(s2.support);
        final Set<Set<Integer>> seen = new HashSet<>();
        seen.add(s2.support);

        if (ties(ctx, s1Fixed, s2, plan) && seen.add(s1Fixed.support)) {
            candidates.add(s1Fixed);
            cuts.add(s1Fixed.support);
        }
        final int max = (int) ctx.heuristics.numerics()
            .effort(ctx.heuristics.numerics().maxTiedSupports);
        while (candidates.size() < max && !ctx.budget.expired()) {
            final SolveResult nextResult = Solver.quantityMILP(ctx, plan.weightedCap(), cuts, plan.scale());
            if (nextResult.isRejected()) break;
            final StageOutcome next = nextResult.point();
            if (!ties(ctx, next, s2, plan)) {
                break;
            }
            // The no-good cuts are written over the binaries, but y_g = 1 with zero flow
            // satisfies the one-directional big-M link: whenever the cap leaves a gate spare, the
            // solver can dodge a cut by opening one and hand back an already-given support. Dedupe
            // on the flow-derived support and stop on a repeat, so the loop provably progresses.
            if (!seen.add(next.support)) break;
            candidates.add(next);
            cuts.add(next.support);
        }
        return candidates;
    }

    /** Whether a witness matches the stage-2 optimum on quantity and still fits the gate count's cap. */
    private static boolean ties(final SolveContext ctx, final StageOutcome candidate, final StageOutcome s2,
        final GateCountStage.GatePlan plan) {
        return candidate.externalQuantity <= s2.externalQuantity + ctx.tieTolerance(s2.externalQuantity, s2)
            && ctx.weightedCost(candidate.support) <= plan.weightedCap() + 0.5;
    }

    /**
     * The external-quantity stage's hand-off: the candidate points tied at the (gate count,
     * quantity) optimum that the internal-flow stage chooses between, the point grown from stage
     * 1's own certified support (kept by identity for the tie-break), and the quantity cap every
     * candidate is held to.
     */
    public record ExternalPlan(Optional<StageOutcome> s1Fixed, List<StageOutcome> candidates, double qtyCap) {

        public ExternalPlan {
            candidates = List.copyOf(candidates);
        }
    }
}
