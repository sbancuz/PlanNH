package com.sbancuz.plannh.data.flowchart.balancer.stages;

import java.util.Set;

import com.sbancuz.plannh.data.flowchart.balancer.Outcome;
import com.sbancuz.plannh.data.flowchart.balancer.SolveContext;
import com.sbancuz.plannh.data.flowchart.balancer.SolveResult;
import com.sbancuz.plannh.data.flowchart.balancer.Solver;
import com.sbancuz.plannh.data.flowchart.balancer.Stage;
import com.sbancuz.plannh.data.flowchart.balancer.StageOutcome;

/**
 * Internal flow: gates fixed to the open set, minimize total internal flow, then pick the one
 * canonical point among the optimum. Each external-quantity candidate opens the gates ITS OWN
 * witness carries and is solved under the shared quantity cap; ties keep the least internal flow,
 * then the candidate grown from the gate count's certified support, then the lower ChoiceKey. The
 * champion is canonicalized once more and committed to the context.
 */
public final class FlowMinStage implements Stage<ExternalMinStage.ExternalPlan, StageOutcome> {

    @Override
    public String name() {
        return "internal flow";
    }

    @Override
    public Outcome<StageOutcome> run(final SolveContext ctx, final ExternalMinStage.ExternalPlan plan) {
        StageOutcome best = null;
        Set<Integer> bestSupport = null;
        boolean bestFromGateCount = false;
        for (final StageOutcome cand : plan.candidates()) {
            final Set<Integer> open = ctx.carryingGates(cand.externals, cand.support);
            // Identity tie-break preserved through the Optional: only the exact point the gate
            // count's own fixed solve produced outranks a flow tie.
            final boolean fromGateCount = plan.s1Fixed()
                .filter(fixed -> cand == fixed)
                .isPresent();
            final SolveResult s3Result = Solver.flowMinimal(ctx, open, plan.qtyCap());
            if (s3Result.isRejected()) continue;
            StageOutcome s3 = Solver.canonicalize(ctx, open, s3Result.point());
            if (best == null) {
                best = s3;
                bestSupport = s3.support;
                bestFromGateCount = fromGateCount;
                continue;
            }
            // Least internal flow wins. A tie there is a tie on every objective the solver has, so
            // it breaks on two rules that are properties of the chart rather than of the search:
            // the candidate grown from the gate count's certified support first, then the lower
            // ChoiceKey.
            final double tol = ctx.tieTolerance(best.internalFlow, best);
            final boolean better = s3.internalFlow < best.internalFlow - tol;
            final boolean tied = Math.abs(s3.internalFlow - best.internalFlow) <= tol;
            final boolean tiedButPreferred = tied
                && (fromGateCount && !bestFromGateCount || fromGateCount == bestFromGateCount && ctx.keyOf(s3.support)
                    .compareTo(ctx.keyOf(bestSupport)) < 0);
            if (better || tiedButPreferred) {
                // The support the returned point actually carries, not the candidate that led to
                // it: those differ whenever this stage leaves one of the candidate's gates unused.
                best = s3;
                bestSupport = s3.support;
                bestFromGateCount = fromGateCount;
            }
        }
        if (best == null) {
            return new Outcome.Fail<>(ctx.rejection);
        }
        ctx.commit(best);
        return new Outcome.Continue<>(best);
    }
}
