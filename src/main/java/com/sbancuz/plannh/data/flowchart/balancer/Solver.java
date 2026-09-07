package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.List;
import java.util.Set;

import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

/**
 * The LP/MILP machinery shared by every stage. Model ASSEMBLY now lives in one fluent place,
 * {@link ModelBuilder} - a stage declares its variables, its row families and its objective as a
 * chain and calls {@code solve()} - and this class holds the higher-level solves the AUTO chain is
 * built from (fast path, stage-1 filter/certification, stage-2 quantity, stage-3 flow and
 * canonicalization) plus the readers that turn a solved model into a {@link StageOutcome}.
 *
 * <p>
 * Keeping the math in one place lets a new stage be written against known-good primitives;
 * {@link ModelBuilder#portRows(boolean)} already lets the OUTPUT / INPUT stage express its own
 * (extern-free) family without a bespoke builder.
 */
public final class Solver {

    /**
     * A tie-break on the import variables of the extent-flow rescue, not a real cost: the count
     * objective (1.0 per machine) dominates, so the model still uses the fewest machines and only
     * then pulls the least it must from outside. Small enough that no realistic shortfall can
     * outweigh a machine.
     */
    private static final double IMPORT_WEIGHT = 1e-8;

    private Solver() {}

    /**
     * The one place a model runs, so the profiler sees every solve. Timed and sized only when a
     * profiler is attached (the {@code disabled} singleton never pays for the clock read).
     */
    public static Optimisation.Result solve(final SolveContext ctx, final Handles h, final String label) {
        final Profiler profiler = ctx.profiler;
        final long start = profiler.enabled() ? System.currentTimeMillis() : 0;
        final Optimisation.Result result = h.model()
            .minimise();
        if (profiler.enabled()) {
            profiler.modelSolved(
                label,
                System.currentTimeMillis() - start,
                result.getState()
                    .toString(),
                h.model()
                    .countVariables(),
                h.model()
                    .countExpressions());
        }
        return result;
    }

    /** The AUTO extent lower bound: the pass-2 floors when a replay forced them, else zero. */
    private static double[] autoLower(final SolveContext ctx) {
        final int n = ctx.model.machines.size();
        final double[] lower = new double[n];
        if (ctx.floors.length > 0) {
            System.arraycopy(ctx.floors, 0, lower, 0, n);
        }
        return lower;
    }

    /**
     * Stage 3 / zero-gate fast path: gates fixed to {@code open}, minimize total internal flow.
     * Empty support and a zero cap make this the fast path (0 gates, 0 externals is trivially optimal).
     */
    public static SolveResult flowMinimal(final SolveContext ctx, final Set<Integer> open, final double qtyCap) {
        final Handles h = ModelBuilder.over(ctx)
            .extents(autoLower(ctx))
            .pools()
            .flows()
            .externals()
            .conservation()
            .handles();
        closeGatesOutside(ctx, h, open);
        if (!open.isEmpty()) holdQuantity(ctx, h, qtyCap);
        flowObjective(h);
        final String label = open.isEmpty() ? "fast path" : "stage 3 flow";
        final Optimisation.Result result = solve(ctx, h, label);
        if (!isUsable(result)) return rejected(ctx, result);
        return fromHandles(ctx, h, false);
    }

    /** LP over the whole gate structure, raw-quantity objective; seeds the deletion filter. */
    public static SolveResult externalsLp(final SolveContext ctx, final Set<Integer> open) {
        final Handles h = ModelBuilder.over(ctx)
            .extents(autoLower(ctx))
            .pools()
            .flows()
            .externals()
            .conservation()
            .handles();
        for (int p = 0; p < ctx.model.connectedPorts.size(); p++) {
            // null open = the permissive probe (every gate open, every external free):
            // diagnosePins uses it to test whether the pins are the conflict, and it must not
            // clamp anything or it would report infeasible.
            if (open != null && !open.contains(ctx.model.portGate[p])) {
                h.extVars()[p].upper(0);
            } else {
                h.extVars()[p].weight(ctx.importTilt(ctx.model.portGate[p]));
            }
        }
        final Optimisation.Result result = solve(ctx, h, "externals");
        if (!isUsable(result)) return rejected(ctx, result);
        return fromHandles(ctx, h, false);
    }

    /** Stage 1 exact MILP (certification): proves the gate-count optimum within the node budget. */
    public static SolveResult gateMILP(final SolveContext ctx, final Double upperBoundCost, final double scale) {
        final Numerics n = ctx.heuristics.numerics();
        double bigM = n.bigMFactor * scale;
        for (int growth = 0; growth <= n.maxMGrowths; growth++) {
            final Handles h = ModelBuilder.over(ctx)
                .extents(autoLower(ctx))
                .pools()
                .flows()
                .externals()
                .gates(bigM)
                .conservation()
                .nodeBudget(n.milpCertNodeBudget)
                .handles();
            final Expression ub = h.model()
                .addExpression("ub_cut");
            for (int g = 0; g < ctx.model.gates.size(); g++) {
                final double weight = ctx.gateWeight(g);
                h.gateVars()[g].weight(weight);
                if (ub != null) ub.set(h.gateVars()[g], weight);
            }
            if (ub != null) ub.upper(upperBoundCost + 0.5);
            // Epsilon cost on every external so a costless external cannot sit at an arbitrary
            // vertex (which presses the big-M cap and pollutes the flow-derived support). Sized
            // after bigM so one external at its cap costs a thousandth of a gate on any scale.
            final double anchor = 1e-3 / bigM;
            for (final Variable ext : h.extVars()) {
                ext.weight(anchor);
            }
            final long solveStart = System.currentTimeMillis();
            final Optimisation.Result result = solve(ctx, h, "stage 1 gate MILP");
            final boolean withinValve = System.currentTimeMillis() - solveStart < h.model().options.time_abort;
            if (!isUsable(result)) return rejected(ctx, result);
            if (pressesCap(h, bigM)) {
                ctx.profiler.bigMGrew("stage 1 gate MILP", bigM);
                bigM *= 10;
                continue;
            }
            return fromHandles(
                ctx,
                h,
                withinValve && result.getState()
                    .isOptimal());
        }
        return SolveResult.rejected(ctx.rejection);
    }

    /** Least external quantity over a fixed support: no binaries, so a plain LP. */
    public static SolveResult fixedQuantity(final SolveContext ctx, final Set<Integer> open) {
        final Handles h = ModelBuilder.over(ctx)
            .extents(autoLower(ctx))
            .pools()
            .flows()
            .externals()
            .conservation()
            .handles();
        closeGatesOutside(ctx, h, open);
        for (int p = 0; p < ctx.model.connectedPorts.size(); p++) {
            h.extVars()[p].weight(ctx.externalWeight(p));
        }
        final Optimisation.Result result = solve(ctx, h, "stage 2 fixed quantity");
        if (!isUsable(result)) return rejected(ctx, result);
        return fromHandles(ctx, h, false);
    }

    /** Stage 2 MILP: least external quantity under the stage-1 gate-cap cut, free over supports. */
    public static SolveResult quantityMILP(final SolveContext ctx, final double weightedCap,
        final List<Set<Integer>> cuts, final double scale) {
        final Numerics n = ctx.heuristics.numerics();
        double bigM = n.bigMFactor * scale;
        for (int growth = 0; growth <= n.maxMGrowths; growth++) {
            final Handles h = ModelBuilder.over(ctx)
                .extents(autoLower(ctx))
                .pools()
                .flows()
                .externals()
                .gates(bigM)
                .conservation()
                .handles();
            final Expression cap = h.model()
                .addExpression("count_cap");
            for (int g = 0; g < ctx.model.gates.size(); g++) {
                cap.set(h.gateVars()[g], ctx.gateWeight(g));
            }
            cap.upper(weightedCap + 0.5);
            for (int p = 0; p < ctx.model.connectedPorts.size(); p++) {
                h.extVars()[p].weight(ctx.externalWeight(p));
            }
            addNoGoodCuts(h, cuts);
            final Optimisation.Result result = solve(ctx, h, "stage 2 quantity MILP");
            if (!isUsable(result)) return rejected(ctx, result);
            if (pressesCap(h, bigM)) {
                ctx.profiler.bigMGrew("stage 2 quantity MILP", bigM);
                bigM *= 10;
                continue;
            }
            return fromHandles(ctx, h, false);
        }
        return SolveResult.rejected(ctx.rejection);
    }

    /** The one canonical point among a stage-3 optimum: redistributes within it, deterministically. */
    public static StageOutcome canonicalize(final SolveContext ctx, final Set<Integer> open, final StageOutcome s3) {
        final Handles h = ModelBuilder.over(ctx)
            .extents(autoLower(ctx))
            .pools()
            .flows()
            .externals()
            .conservation()
            .handles();
        final Expression qty = open.isEmpty() ? null
            : h.model()
                .addExpression("qty_cap");
        final double qtyEps = ctx.heuristics.numerics().qtyEps;
        final int ports = ctx.model.connectedPorts.size();
        for (int p = 0; p < ports; p++) {
            if (qty != null && open.contains(ctx.model.portGate[p])) {
                qty.set(h.extVars()[p], ctx.externalWeight(p));
                h.extVars()[p].weight(rank(p, ports) * ctx.externalWeight(p));
            } else {
                h.extVars()[p].upper(0);
            }
        }
        if (qty != null) {
            qty.upper(s3.externalQuantity * (1 + qtyEps) + qtyEps);
        }
        final Expression flowCap = h.model()
            .addExpression("flow_cap");
        for (int e = 0; e < h.flowVars().length; e++) {
            flowCap.set(h.flowVars()[e], 1.0);
            h.flowVars()[e].weight(rank(e, h.flowVars().length));
        }
        flowCap.upper(s3.internalFlow * (1 + qtyEps) + qtyEps * Math.max(1.0, s3.internalFlow));
        final Optimisation.Result result = solve(ctx, h, "canonicalise");
        if (!isUsable(result)) return s3; // the uncanonical point is still a correct answer
        final StageOutcome canonical = fromHandles(ctx, h, false).point();
        return canonical == null ? s3 : canonical;
    }

    /** Position {@code i} of {@code n} mapped into [1, 2]: a tie-break, not a cost. */
    public static double rank(final int i, final int n) {
        return n <= 1 ? 1.0 : 1.0 + (double) i / (n - 1);
    }

    public static void closeGatesOutside(final SolveContext ctx, final Handles h, final Set<Integer> open) {
        for (int p = 0; p < ctx.model.connectedPorts.size(); p++) {
            if (!open.contains(ctx.model.portGate[p])) h.extVars()[p].upper(0);
        }
    }

    public static void addNoGoodCuts(final Handles h, final List<Set<Integer>> cuts) {
        int i = 0;
        for (final Set<Integer> cut : cuts) {
            final Expression e = h.model()
                .addExpression("no_good_" + i++);
            for (int g = 0; g < h.gateVars().length; g++) {
                e.set(h.gateVars()[g], cut.contains(g) ? -1.0 : 1.0);
            }
            e.lower(1.0 - cut.size());
        }
    }

    private static boolean pressesCap(final Handles h, final double bigM) {
        for (final Variable ext : h.extVars()) {
            final Number v = ext.getValue();
            if (v != null && v.doubleValue() > 0.9 * bigM) return true;
        }
        return false;
    }

    private static boolean isUsable(final Optimisation.Result result) {
        return result.getState()
            .isFeasible();
    }

    /**
     * Records what the solver actually said and yields the rejection every caller reads as
     * "nothing here". The state does not separate a model with no solution from a search that gave
     * up looking - ojAlgo returns INFEASIBLE for both - so whether the budget was spent goes into
     * the reason too.
     */
    private static SolveResult rejected(final SolveContext ctx, final Optimisation.Result result) {
        final Note note = ctx.budget.expired() ? new Note(SolverMessage.SOLVER_BUDGET, result.getState())
            : new Note(SolverMessage.SOLVER_UNSATISFIABLE, result.getState());
        ctx.profiler.pointRejected("solve", note.describe());
        return SolveResult.rejected(note);
    }

    private static void holdQuantity(final SolveContext ctx, final Handles h, final double optimum) {
        final Expression cap = h.model()
            .addExpression("hold_least_excess");
        for (int p = 0; p < ctx.model.connectedPorts.size(); p++) {
            cap.set(h.extVars()[p], ctx.externalWeight(p));
        }
        final double slack = ctx.heuristics.numerics().qtyEps;
        cap.upper(optimum * (1 + slack) + slack);
    }

    private static void flowObjective(final Handles h) {
        for (final Variable v : h.flowVars()) {
            v.weight(1.0);
        }
    }

    public static SolveResult fromHandles(final SolveContext ctx, final Handles h, final boolean provenOptimal) {
        final double[] extents = values(h.extentVars());
        final double[] flows = values(h.flowVars());
        final double[] externals = values(h.extVars());
        final String residual = ctx.validate(extents, flows, externals);
        if (residual != null) {
            final Note note = new Note(SolverMessage.SOLVER_NOT_CONSERVING, residual);
            ctx.profiler.pointRejected("solve", note.describe());
            return SolveResult.rejected(note);
        }
        return SolveResult.solved(StageOutcome.of(ctx, extents, flows, externals, provenOptimal));
    }

    private static double[] values(final Variable[] vars) {
        final double[] out = new double[vars.length];
        for (int i = 0; i < vars.length; i++) {
            final Number v = vars[i].getValue();
            out[i] = v == null ? 0 : v.doubleValue();
        }
        return out;
    }

    /**
     * Reads an extent-flow point (built with {@link ModelBuilder#portRows(boolean)}) with the
     * validation that family actually satisfies - the conservation-with-externals residual that
     * {@link #fromHandles} checks is the AUTO family's, and would falsely reject the OUTPUT /
     * INPUT 'at least' rows. Every claimed (exact) port's residual must be within tolerance of
     * zero, each 'at least' port's residual must stay non-negative (relative to {@code max(1, qty)}),
     * and no variable may sit below zero. Externals carry the imports the at-least rows pulled
     * from outside (zero on the claimed rows and on models built without {@link ModelBuilder#externals()});
     * {@link SolutionView} reports them as boundary "imports" chips. A model built with
     * {@link ModelBuilder#extentCounts()} reads integer machine counts and converts them to rate
     * extents (count * TPS/durTicks) so the point stays in the rate world.
     */
    public static SolveResult readExtentFlowModel(final SolveContext ctx, final Handles h, final boolean inputPriority,
        final boolean provenOptimal, final boolean counts) {
        final double[] countsOut = values(h.extentVars());
        final double[] extents = new double[countsOut.length];
        for (int i = 0; i < countsOut.length; i++) {
            extents[i] = counts ? countsOut[i] * (double) Numerics.TICKS_PER_SECOND / ctx.model.machines.get(i).durTicks
                : countsOut[i];
        }
        final double[] flows = values(h.flowVars());
        // The no-import model declares no externals at all (an empty array), which reads as a zero
        // import on every port rather than an index miss.
        final double[] externals = h.extVars().length == 0 ? new double[ctx.model.connectedPorts.size()]
            : values(h.extVars());
        final double floor = -ctx.heuristics.numerics().dust;
        for (final double extent : extents) {
            if (extent < floor) {
                final Note note = new Note(SolverMessage.SOLVER_NEGATIVE_EXTENT, extent);
                ctx.profiler.pointRejected("extent-min", note.describe());
                return SolveResult.rejected(note);
            }
        }
        for (final double flow : flows) {
            if (flow < floor) {
                final Note note = new Note(SolverMessage.SOLVER_NEGATIVE_FLOW, flow);
                ctx.profiler.pointRejected("extent-min", note.describe());
                return SolveResult.rejected(note);
            }
        }
        final double tol = ctx.heuristics.numerics().validateTol;
        for (int p = 0; p < ctx.model.connectedPorts.size(); p++) {
            final ModelData.ConnectedPort port = ctx.model.connectedPorts.get(p);
            if (port.qtyPerCraft() <= 0 || port.edges()
                .isEmpty()) continue;
            double flowsSum = 0;
            for (final int e : port.edges()) {
                flowsSum += flows[e];
            }
            final double residual = flowsSum + externals[p] - extents[port.machine()] * port.qtyPerCraft();
            final double scale = Math.max(1.0, port.qtyPerCraft());
            final boolean claimed = inputPriority == port.input();
            if (claimed) {
                if (Math.abs(residual) / scale > tol) {
                    final Note note = new Note(SolverMessage.SOLVER_INEXACT_RESIDUAL, residual);
                    ctx.profiler.pointRejected("extent-min", note.describe());
                    return SolveResult.rejected(note);
                }
            } else if (residual / scale < -tol) {
                final Note note = new Note(SolverMessage.SOLVER_UNDER_SUPPLY, residual);
                ctx.profiler.pointRejected("extent-min", note.describe());
                return SolveResult.rejected(note);
            }
        }
        return SolveResult.solved(StageOutcome.of(ctx, extents, flows, externals, provenOptimal));
    }

    /**
     * The OUTPUT / INPUT solve (the {@code ExtentMinStage} build), two-phase so imports never
     * displace normal operation. Phase 1 is the no-import balance - fewest whole machines
     * meeting the priority claims through the drawn edges only, with {@code min Σ count} and no
     * externals - and commits its point unchanged whenever the chart can balance on its own edges.
     * Only when that model is infeasible does phase 2 rescue the chart with external imports, and
     * even then counts stay primary: the SAME {@code min Σ count} objective, the at-least rows now
     * allowed to pull the shortfall from outside. A chart the edges can carry therefore never
     * imports, and a chart they cannot carries only the residual the fewest machines leave, never
     * a whole import the user could have avoided by building one more machine. An import is always
     * a genuine shortfall: a pin or a machine floor the drawn edges cannot cover.
     */
    public static SolveResult extentFlow(final SolveContext ctx, final boolean inputPriority) {
        final ModelBuilder natural = ModelBuilder.over(ctx)
            .extentCounts()
            .pools()
            .extentsWeighted(i -> 1.0)
            .flows()
            .portRows(inputPriority);
        final Optimisation.Result naturalResult = natural.solve("extent-min");
        if (naturalResult.getState()
            .isFeasible()) {
            final Handles h = natural.handles();
            return readExtentFlowModel(
                ctx,
                h,
                inputPriority,
                naturalResult.getState()
                    .isOptimal(),
                true);
        }
        final ModelBuilder rescue = ModelBuilder.over(ctx)
            .extentCounts()
            .pools()
            .extentsWeighted(i -> 1.0)
            .flows()
            .externals()
            .importsWeighted(IMPORT_WEIGHT)
            .portRows(inputPriority);
        final Optimisation.Result rescueResult = rescue.solve("extent-min rescue");
        if (!rescueResult.getState()
            .isFeasible()) {
            return SolveResult.rejected(ctx.rejection);
        }
        final Handles h = rescue.handles();
        return readExtentFlowModel(
            ctx,
            h,
            inputPriority,
            rescueResult.getState()
                .isOptimal(),
            true);
    }
}
