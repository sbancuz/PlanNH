package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Alternatives;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Enumerator;
import com.sbancuz.plannh.data.properties.RecipeProperty;

/**
 * The single entry point of the balancer package. Every {@link BalanceMode} is a {@link Chain}:
 * NONE is an empty chain that leaves the point untested, OUTPUT / INPUT are each one
 * {@code ExtentMinStage} pick, and AUTO is the full lexicographic chain plus its pass-2 replay.
 * The chain is the whole difference between the modes; {@link #balance} is the GUI-facing read-out
 * over that machinery.
 */
public final class Balancer {

    private Balancer() {}

    /**
     * Runs the mode's chain against the chart and returns the context plus its {@link Settlement}
     * (a point, or the reason there is none). Callers that want a fresh budget hand one rebuilt per
     * call; {@link Graph#balance()} thunks the mode and ops-mode here.
     */
    public static RunContext run(final BalanceMode mode, final Graph graph, final boolean opsMode) {
        return run(mode, graph, opsMode, Profiler.disabled());
    }

    /**
     * The same run with an instrumentation hook attached. The profiler's {@code runStarted} event
     * fires with the model's size, each chain element reports through it, and {@code runFinished}
     * hands back a {@link SolutionView} - only ever built here, when a profiler is actually
     * attached, so the default path never constructs it.
     */
    public static RunContext run(final BalanceMode mode, final Graph graph, final boolean opsMode,
        final Profiler profiler) {
        final SolveContext ctx = ctxOf(mode, graph, opsMode, Map.of(), profiler);
        profiler.runStarted(
            mode,
            ctx.model.machines.size(),
            ctx.model.connectedPorts.size(),
            ctx.model.gates.size(),
            ctx.model.edges.size());
        final long start = System.currentTimeMillis();
        // Unpinned chart = wiring, not a problem to solve: AUTO reports the NO_PIN idle answer.
        // The OUTPUT / INPUT modes solve regardless - their "fewest machines" LP needs no scale anchor.
        if (mode == BalanceMode.AUTO && !ctx.anyPin) {
            profiler.runFinished(null, SolverMessage.NO_PIN.describe());
            return new RunContext(ctx, new Settlement.Stalled(SolverMessage.NO_PIN.toNote()));
        }
        final Settlement settlement = mode.chain()
            .run(ctx);
        final SolutionView view = profiler.enabled() && ctx.point() != null
            ? SolutionView.of(ctx, System.currentTimeMillis() - start)
            : null;
        profiler.runFinished(
            view,
            settlement instanceof Settlement.Stalled(Note reason) ? reason.describe() : null);
        return new RunContext(ctx, settlement);
    }

    /** The context after a run plus how it ended: a point, or the reason it stalled. */
    public record RunContext(SolveContext ctx, Settlement settlement) {}

    /** The shared context each entry point runs its mode's chain under; see {@link #run}. */
    private static SolveContext ctxOf(final BalanceMode mode, final Graph graph, final boolean opsMode,
        final Map<UUID, Double> extraExtentPins) {
        return ctxOf(mode, graph, opsMode, extraExtentPins, Profiler.disabled());
    }

    private static SolveContext ctxOf(final BalanceMode mode, final Graph graph, final boolean opsMode,
        final Map<UUID, Double> extraExtentPins, final Profiler profiler) {
        final long budgetMillis = mode.heuristics()
            .numerics().solveBudgetMillis;
        return new SolveContext(
            graph,
            mode.heuristics(),
            Budget.of(budgetMillis),
            opsMode,
            extraExtentPins,
            mode.pins(),
            profiler);
    }

    /**
     * Runs the mode's chain against the chart and then, when the mode has a choices surface
     * ({@link BalanceMode#supportsAlternatives()}), the alternatives search around the committed
     * point. A stored {@link ChoiceKey} that cannot be resolved or needs more gates than the
     * answer produces the same notes the corpus keys on, and the point stays the solver's own.
     */
    public static Alternatives alternatives(final BalanceMode mode, final Graph graph, final boolean opsMode) {
        return alternatives(mode, graph, opsMode, null, Map.of());
    }

    /**
     * As above, but with a stored {@link ChoiceKey} to honour (applied between the solve and the
     * enumeration) and per-machine extent pins.
     */
    public static Alternatives alternatives(final BalanceMode mode, final Graph graph, final boolean opsMode,
                                            @Nullable final ChoiceKey choice, final Map<UUID, Double> extraExtentPins) {
        final SolveContext ctx = ctxOf(mode, graph, opsMode, extraExtentPins);
        if (mode == BalanceMode.AUTO && !ctx.anyPin || ctx.model.machines.isEmpty()) {
            return new Alternatives(null, List.of(), true, List.of());
        }
        final Settlement settlement = mode.chain()
            .run(ctx);
        if (settlement instanceof final Settlement.Stalled stalled || ctx.point() == null) {
            return new Alternatives(null, List.of(), true, List.of());
        }
        if (!mode.supportsAlternatives()) {
            // The simple modes have no choices to offer; still answer with an empty, complete list
            // so the panel renders nothing rather than a broken promise.
            return new Alternatives(ctx.keyOf(ctx.support()), List.of(), true, List.of());
        }
        if (choice != null) {
            ctx.commit(Enumerator.applyChoice(ctx, choice));
        }
        return Enumerator.enumerate(ctx);
    }

    /**
     * The GUI entry: solves and enumerates in one pass, so the panel does not pay for the whole
     * pipeline twice - once to draw the chart and again to ask what else it could have been. Unlike
     * {@link #run}, the {@link SolutionView} is always built: the panel is a real consumer of it
     * (the boundary, the open-gate count, the solver notes), not just the profiler.
     *
     * @param choice          a stored {@link ChoiceKey} to honour between the solve and the
     *                        enumeration, or null for the solver's own answer.
     * @param extraExtentPins per-machine extent pins (crafts/s), e.g. from a target-rate pin.
     */
    public static Answer solveWithAlternatives(final BalanceMode mode, final Graph graph, final boolean opsMode,
        @Nullable final ChoiceKey choice, final Map<UUID, Double> extraExtentPins) {
        final SolveContext ctx = ctxOf(mode, graph, opsMode, extraExtentPins);
        final long start = System.currentTimeMillis();
        if (ctx.model.machines.isEmpty()) {
            return new Answer.Failed(SolverMessage.EMPTY_GRAPH.toNote());
        }
        if (mode == BalanceMode.AUTO && !ctx.anyPin) {
            return new Answer.Failed(SolverMessage.NO_PIN.toNote());
        }
        final Settlement settlement = mode.chain()
            .run(ctx);
        if (settlement instanceof Settlement.Stalled(Note reason)) {
            return new Answer.Failed(reason);
        }
        if (ctx.point() == null) {
            return new Answer.Failed(SolverMessage.BALANCE_FAILED.toNote(ctx.rejection));
        }
        if (choice != null && mode.supportsAlternatives()) {
            ctx.commit(Enumerator.applyChoice(ctx, choice));
        }
        // The independent conservation check is AUTO's contract - the extent-flow family's positive
        // "at least" residuals (OUTPUT / INPUT) legitimately fail it, so those modes never route
        // through this entry.
        if (mode == BalanceMode.AUTO) {
            final String residualError = ctx.validate(ctx.extents(), ctx.flows(), ctx.externals());
            if (residualError != null) {
                return new Answer.Failed(SolverMessage.VALIDATION_FAILED.toNote(residualError));
            }
        }
        final SolutionView view = SolutionView.of(ctx, System.currentTimeMillis() - start);
        final Alternatives alternatives = mode.supportsAlternatives() ? Enumerator.enumerate(ctx)
            : new Alternatives(ctx.keyOf(ctx.support()), List.of(), true, List.of());
        return new Answer.Solved(view, alternatives);
    }

    /** A solved chart plus the other answers it could have had, or the failure that prevented both. */
    public sealed interface Answer permits Answer.Solved,Answer.Failed {

        /** The solve committed a usable point and the enumeration that goes with it. */
        record Solved(SolutionView solution, Alternatives alternatives) implements Answer {}

        /** The solve committed no point; {@code failure} is why. */
        record Failed(Note failure) implements Answer {}
    }

    /**
     * The GUI dispatcher: every solving mode runs the SAME engine - build a context, run the
     * mode's own {@link Chain}, and derive the result from the committed point's {@link SolutionView}
     * - so a new balancer is a new {@link BalanceMode} constant and nothing else. Only NONE is a
     * separate branch, because it never solves: the chart's configured counts as-is, which the chain
     * cannot express (an empty chain stalls rather than succeeding-without-a-point).
     */
    @Nonnull
    public static BalanceResult balance(final Graph graph, final BalanceMode mode, final boolean opsMode) {
        if (mode == BalanceMode.NONE) {
            return buildResultFractional(graph, configuredCounts(graph), List.of(), null, null);
        }
        // One pass produces both the chart and the answers it could have had: the panel shows the
        // alternatives unconditionally now, and re-deriving them would mean solving twice per edit.
        final Answer answer = solveWithAlternatives(mode, graph, opsMode, graph.getExcessChoice(), Map.of());
        if (answer instanceof Answer.Failed(Note reason)) {
            if (reason != null && reason.message() == SolverMessage.NO_PIN) {
                // Expected state, not an error: an unpinned chart is just wiring, so it gets no
                // quantities at all rather than numbers derived from an anchor nobody set.
                PlanNH.LOG.info("{} balance idle: {}", mode, reason.describe());
            } else {
                PlanNH.LOG.warn(
                    "{} balance failed ({}); showing the chart without solve-derived quantities",
                    mode,
                    reason == null ? "no point" : reason.describe());
            }
            // A stalled AUTO shows the chart without quantities and the reason; the simple modes
            // keep the configured counts - their solve is a refinement that may be refused.
            final List<Note> failureNotes = reason == null ? List.of() : List.of(reason);
            return mode == BalanceMode.AUTO ? buildResultFractional(graph, Map.of(), failureNotes, null, null)
                : buildResultFractional(graph, configuredCounts(graph), failureNotes, null, null);
        }
        final Answer.Solved solved = (Answer.Solved) answer;
        final SolutionView view = solved.solution();
        for (final Note note : view.notes) {
            PlanNH.LOG.info("{} balance: {}", mode, note.describe());
        }
        PlanNH.LOG.info(
            "{} balance: {} machines, {} open gates, {}ms",
            mode,
            view.machineCounts.size(),
            view.openGates,
            view.wallMillis);
        return buildResultFractional(graph, view.machineCounts, view.notes, view, solved.alternatives());
    }

    /**
     * The configured machine counts: NONE's answer, and the simple modes' answer when their solve
     * is refused. A chart with no solve-derived quantities reads its own configuration.
     */
    @Nonnull
    private static Map<UUID, Double> configuredCounts(final Graph graph) {
        final Map<UUID, Double> counts = new HashMap<>();
        for (final Node node : graph.getNodes()
            .values()) {
            counts.put(
                node.getId(),
                (double) node.getMachineConfig()
                    .getMachineCount());
        }
        return counts;
    }

    /**
     * Builds the balance result from (possibly fractional) machine counts. Every displayed
     * number derives from the exact fractional count; rounding up for placement is left to the
     * reader. A machine missing from {@code machineCounts} reads zero - the way a chart with no
     * solve-derived quantities is presented.
     */
    @Nonnull
    private static BalanceResult buildResultFractional(final Graph graph, final Map<UUID, Double> machineCounts,
        final List<Note> notes, @Nullable final SolutionView auto, @Nullable final Alternatives alternatives) {
        final Map<UUID, NodeBalance> nodeBalances = new HashMap<>();
        final Map<RecipeProperty<?>, Long> propertyTotals = new HashMap<>();
        double totalOps = 0;
        int totalDuration = 0;

        for (final Node node : graph.getNodes().values()) {
            if (node.getExtractor() == null) continue; // incorrectly loaded node
            final double count = machineCounts.getOrDefault(node.getId(), 0.0);
            totalOps += count;

            final MachineConfig cfg = node.getMachineConfig();
            final var eff = cfg.computeEffect(node.getProperties());
            final long eutPerOp = eff.energyPerT();
            final int durPerOp = eff.durationTicks();
            final int throughputFactor = eff.throughputFactor();

            final long totalEnergy = Math.round(eutPerOp * durPerOp * count);
            if (durPerOp > totalDuration) totalDuration = durPerOp;

            final Map<Integer, Float> effOuts = new HashMap<>(node.getOutputs().size());
            for (int i = 0; i < node.getOutputs().size(); i++) {
                final var outStack = node.getOutputs().get(i);
                final int stackSize = outStack.getAmount();
                if (stackSize <= 0) continue;
                final float total = (float) (count * stackSize * outStack.getChance()
                    * cfg.outputMultiplier(i)
                    * throughputFactor);
                if (total <= 0) continue;
                effOuts.put(i, total);
            }

            final Map<Integer, Float> effIns = new HashMap<>(node.getInputs().size());
            for (int i = 0; i < node.getInputs().size(); i++) {
                final var inStack = node.getInputs().get(i);
                final int stackSize = inStack.getAmount();
                if (stackSize <= 0) continue;
                final float total = (float) (count * stackSize * inStack.getChance()
                    * cfg.inputMultiplier(i)
                    * throughputFactor);
                if (total <= 0) continue;
                effIns.put(i, total);
            }

            nodeBalances.put(node.getId(), new NodeBalance(count, durPerOp, totalEnergy, durPerOp, effOuts, effIns));

            for (final Map.Entry<RecipeProperty<?>, Object> entry : node.getProperties().entrySet()) {
                if (entry.getValue() instanceof final Number num) {
                    propertyTotals.merge(entry.getKey(), Math.round(num.longValue() * count), Long::sum);
                }
            }
        }

        if (auto == null) {
            return new BalanceResult.Fallback(nodeBalances, propertyTotals, totalOps, totalDuration, notes);
        }
        return new BalanceResult.Solved(nodeBalances, propertyTotals, totalOps, totalDuration, notes, auto, alternatives);
    }

    /** One machine's share of a solved balance, for the node widget and the machine-count panel. */
    public record NodeBalance(double operations, int totalDurationTicks, long totalEnergy, int durationPerOp,
        Map<Integer, Float> effectiveOutputs, Map<Integer, Float> effectiveInputs) {}

}
