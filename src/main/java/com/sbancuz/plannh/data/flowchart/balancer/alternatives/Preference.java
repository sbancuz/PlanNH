package com.sbancuz.plannh.data.flowchart.balancer.alternatives;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntToDoubleFunction;

import javax.annotation.Nullable;

import com.sbancuz.plannh.data.flowchart.balancer.SolveContext;
import com.sbancuz.plannh.data.flowchart.balancer.SolverMessage;
import com.sbancuz.plannh.data.flowchart.balancer.StageOutcome;

/**
 * One rule for choosing between answers that every rule before it found equally good. A chart
 * normally has many balanced solutions and no fact that separates them, so AUTO ranks them by an
 * ordered list of preferences, each optimized subject to the optima of the ones before it. A
 * preference is fully described by which family of model variables carries its cost and what each
 * of those is worth: making it an objective, reading it off a solved point and holding it while a
 * later preference breaks the remaining ties all follow from that pair. The sequence, its measures
 * and the {@link Rank} outcomes are part of the choices panel and the persisted alternatives, so
 * they must not change.
 *
 * <p>
 * {@link #ORDER} is the single authority for the sequence: the choices enumerator names the first
 * entry that separates a rejected answer, and the panel sorts by {@link #displayOrder()} which
 * derives from the same sequence, so a hand-kept list cannot drift from it.
 */
public record Preference(SolverMessage name, Family family, Weight weight, @Nullable Rank whenWorse,
    @Nullable Rank whenBetter) {

    /** Which family of model variables carries a preference's cost. */
    public enum Family {
        /** One binary per gate; only present in a model built with binaries. */
        GATES,
        /** One per connected port: what crosses the chart's boundary there. */
        EXTERNALS,
        /** One per drawn edge. */
        FLOWS
    }

    @FunctionalInterface
    public interface Weight {

        double of(SolveContext ctx, int index);
    }

    /**
     * AUTO's answer, in the order it prefers things. Only the first two are decided
     * combinatorially - they choose WHICH gates open, which needs binaries - and the rest are plain
     * LPs over the support that search settles on.
     */
    public static final List<Preference> ORDER = List.of(
        new Preference(SolverMessage.PREF_FEWEST_GATES, Family.GATES, (ctx, gate) -> 1.0, null, null),
        new Preference(
            SolverMessage.PREF_FEWEST_IMPORTS,
            Family.GATES,
            (ctx, gate) -> ctx.model.gates.get(gate)
                .input() ? 1.0 : 0.0,
            Rank.IMPORTS_INSTEAD,
            null),
        new Preference(
            SolverMessage.PREF_LEAST_EXCESS,
            Family.EXTERNALS,
            (ctx, port) -> ctx.heuristics.externalWeight(
                ctx.model.connectedPorts.get(port)
                    .qtyPerCraft()),
            Rank.VOIDS_MORE,
            null),
        new Preference(
            SolverMessage.PREF_LEAST_FLOW,
            Family.FLOWS,
            (ctx, edge) -> 1.0,
            Rank.MOVES_MORE,
            Rank.MOVES_LESS));

    /**
     * Display order, derived from {@link #ORDER} rather than written beside it: the default, then
     * what beats it, then what ties it, then what it gave up - mildest concession first, so the
     * LATEST preference given up sorts best. A hand-kept list drifts from the preference sequence
     * and silently sorts an unlisted rank to the front.
     */
    public static List<Rank> displayOrder() {
        final List<Rank> order = new ArrayList<>();
        order.add(Rank.DEFAULT);
        for (int i = ORDER.size() - 1; i >= 0; i--) {
            final Rank better = ORDER.get(i).whenBetter;
            if (better != null) order.add(better);
        }
        order.add(Rank.EQUALLY_VALID);
        for (int i = ORDER.size() - 1; i >= 0; i--) {
            final Rank worse = ORDER.get(i).whenWorse;
            if (worse != null) order.add(worse);
        }
        return List.copyOf(order);
    }

    /** What this preference costs at a solved point. */
    public double measure(final SolveContext ctx, final StageOutcome solve) {
        return measure(ctx, solve.support, solve.externals, solve.flows);
    }

    /** As above, for a point held as bare arrays rather than as a solve. */
    public double measure(final SolveContext ctx, final Set<Integer> support, final double[] externals,
        final double[] flows) {
        return switch (family) {
            case GATES -> costOf(support, gate -> weight.of(ctx, gate));
            case EXTERNALS -> weighted(externals, port -> weight.of(ctx, port));
            case FLOWS -> weighted(flows, edge -> weight.of(ctx, edge));
        };
    }

    /**
     * Why {@code alt} is not the default: the earliest preference that separates the two, which is
     * the one that actually decided it. mk1's alternative both imports and moves less material, and
     * reporting the flow would hide the thing that really chose - the direction tilt, which is
     * settled before flow is ever looked at.
     */
    public static Rank rankOf(final SolveContext ctx, final StageOutcome alt, final double[] incumbent,
        final double tolerance) {
        final int n = Math.min(ORDER.size(), incumbent.length);
        for (int i = 0; i < n; i++) {
            final Preference preference = ORDER.get(i);
            // Gate costs are whole numbers packed into one objective; everything else is a rate.
            final double slack = preference.family() == Family.GATES ? 0.5 : tolerance;
            final double mine = preference.measure(ctx, alt);
            if (mine > incumbent[i] + slack && preference.whenWorse != null) return preference.whenWorse;
            if (mine < incumbent[i] - slack && preference.whenBetter != null) return preference.whenBetter;
        }
        return Rank.EQUALLY_VALID;
    }

    private double costOf(final Set<Integer> support, final IntToDoubleFunction weightOf) {
        double total = 0;
        for (final int gate : support) {
            total += weightOf.applyAsDouble(gate);
        }
        return total;
    }

    private double weighted(final double[] values, final IntToDoubleFunction weightOf) {
        double total = 0;
        for (int i = 0; i < values.length; i++) {
            total += values[i] * weightOf.applyAsDouble(i);
        }
        return total;
    }
}
