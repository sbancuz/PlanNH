package com.sbancuz.plannh.data.flowchart.balancer.alternatives;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.sbancuz.plannh.data.flowchart.balancer.Budget;
import com.sbancuz.plannh.data.flowchart.balancer.ChoiceKey;
import com.sbancuz.plannh.data.flowchart.balancer.External;
import com.sbancuz.plannh.data.flowchart.balancer.ModelData;
import com.sbancuz.plannh.data.flowchart.balancer.Note;
import com.sbancuz.plannh.data.flowchart.balancer.Numerics;
import com.sbancuz.plannh.data.flowchart.balancer.PortRef;
import com.sbancuz.plannh.data.flowchart.balancer.SolveContext;
import com.sbancuz.plannh.data.flowchart.balancer.SolveResult;
import com.sbancuz.plannh.data.flowchart.balancer.Solver;
import com.sbancuz.plannh.data.flowchart.balancer.SolverMessage;
import com.sbancuz.plannh.data.flowchart.balancer.StageOutcome;

/**
 * The choices/alternatives search for AUTO: every answer worth showing for the chart the solve
 * just answered, the solver's own first. Separate from the stages because it costs an LP per
 * candidate swap, worth paying only when somebody is looking. It REUSES the stage machinery -
 * {@link Solver#fixedQuantity}, {@link Solver#flowMinimal} and {@link Solver#canonicalize} - it
 * does not run as a stage itself.
 *
 * <p>
 * The bar for being listed is NOT DOMINATED rather than TIED: past the gate count every rule that
 * narrows the field is a preference (the source tilt, "least material moved", and so on), so
 * anything with the same gate count and no worse excess is shown, carrying the reason it is not
 * the default ({@link Rank}). The swap neighbourhood, the shortlist policies, the option cap and
 * its candid "not tried" note, and the sort orders are the persisted-across-versions behaviour
 * the corpus locks.
 */
public final class Enumerator {

    private Enumerator() {}

    /**
     * The answers around the committed point on {@code ctx}, using the context that already solved
     * it. Pass-2 floors are read from {@code ctx.floors}, which the runner leaves in place when a
     * floored answer was committed.
     */
    public static Alternatives enumerate(final SolveContext ctx) {
        final Set<Integer> incumbent = ctx.support();
        final ChoiceKey chosen = ctx.keyOf(incumbent);
        final List<Alternative> options = new ArrayList<>();
        // One "this is what it does now" row per open gate, so every question in the list has its
        // current answer sitting at the top of its own group rather than one row standing for all
        // of them at once.
        for (final int gate : sorted(incumbent)) {
            options.add(asAlternative(ctx, ctx.externals(), incumbent, gate, gate, Rank.DEFAULT));
        }
        final List<Note> notes = new ArrayList<>();

        // Nothing else could open, so nothing else could differ.
        if (incumbent.isEmpty() || ctx.model.gates.size() == incumbent.size()) {
            return new Alternatives(chosen, List.copyOf(options), true, List.copyOf(notes));
        }

        final double flowStar = sum(ctx.flows());
        final double qtyStar = ctx.normalizedQuantity(ctx.externals());
        // The incumbent measured once, in the same order an alternative will be measured against it.
        final double[] incumbentCost = new double[Preference.ORDER.size()];
        for (int i = 0; i < incumbentCost.length; i++) {
            incumbentCost[i] = Preference.ORDER.get(i)
                .measure(ctx, incumbent, ctx.externals(), ctx.flows());
        }
        final double tol = ctx.heuristics.numerics().tieRel
            * Math.max(Math.max(flowStar, qtyStar), ctx.solutionScale(ctx.extents(), ctx.flows(), ctx.externals()));

        final Numerics n = ctx.heuristics.numerics();
        // One gate swapped at a time. Equal gate count by construction, so stage 1's optimum holds
        // and no MILP is needed; either direction, because the whole point is to surface the
        // source/sink tilt rather than let it delete a candidate before anyone sees it.
        final long altBudget = n.effort(n.altBudgetMillis);
        final Budget whole = Budget.of(altBudget);
        ctx.budget = Budget.of(altBudget * n.altSearchSharePercent / 100);
        final Set<ChoiceKey> seen = new HashSet<>();
        seen.add(chosen);
        // Two passes: the neighbourhood is large and most of it is not feasible. A stage-2 LP alone
        // answers "does this support work at all, and how much does it lean on the outside" for one
        // solve; only the shortlist that survives pays for stage 3 and canonicalization.
        final List<Swap> shortlist = new ArrayList<>();
        int evaluated = 0;
        int skipped = 0;
        for (final int out : sorted(incumbent)) {
            for (int in = 0; in < ctx.model.gates.size(); in++) {
                if (incumbent.contains(in)) continue;
                if (evaluated >= n.effort(n.maxAltSwaps) || ctx.budget.expired()) {
                    skipped++;
                    continue;
                }
                evaluated++;
                final Set<Integer> trial = new HashSet<>(incumbent);
                trial.remove(out);
                trial.add(in);
                final SolveResult s2Result = Solver.fixedQuantity(ctx, trial);
                if (s2Result.isRejected() || s2Result.point().support.size() != incumbent.size()) continue;
                final StageOutcome s2 = s2Result.point();
                if (!seen.add(ctx.keyOf(s2.support))) continue;
                shortlist.add(new Swap(out, in, s2));
            }
        }

        // Whatever the search did not use now belongs to the evaluation.
        ctx.budget = whole;

        // Cheapest on the outside world first, so a budget that runs out takes the least
        // interesting answers with it rather than an arbitrary slice.
        shortlist.sort(
            Comparator.<Swap, Double>comparing(c -> c.witness().externalQuantity)
                .thenComparing(c -> ctx.keyOf(c.witness().support)));

        // Round-robin over the decisions rather than straight down the sorted list, so a chart
        // asking two questions where one has six cheap answers does not spend the whole option
        // budget on that one and leave the other with just its heading.
        final List<Swap> fair = interleaveByDecision(shortlist);
        for (final Swap candidate : fair) {
            if (options.size() >= n.maxAltOptions || ctx.budget.expired()) {
                skipped++;
                continue;
            }
            final StageOutcome witness = candidate.witness();
            final Set<Integer> open = ctx.carryingGates(witness.externals, witness.support);
            final SolveResult s3Result = Solver.flowMinimal(ctx, open, witness.externalQuantity);
            if (s3Result.isRejected()) continue;
            final StageOutcome alt = Solver.canonicalize(ctx, open, s3Result.point());
            if (alt.support.size() != incumbent.size()) continue;
            options.add(
                asAlternative(
                    ctx,
                    alt.externals,
                    alt.support,
                    candidate.out(),
                    candidate.in(),
                    Preference.rankOf(ctx, alt, incumbentCost, tol)));
        }
        // Default first, then the ones that cost nothing to prefer, then by how much they give up.
        // Ties inside a rank go to the lower key, so the same chart always lists in the same order.
        final List<Rank> displayOrder = Preference.displayOrder();
        options.sort(
            Comparator.comparing((final Alternative option) -> option.replaces(), PortRef.ORDER)
                .thenComparingInt(option -> displayOrder.indexOf(option.rank()))
                .thenComparing(Alternative::key));
        boolean complete = skipped == 0;
        if (options.size() > n.maxAltOptions) {
            notes.add(new Note(SolverMessage.SHOWING_CLOSEST, n.maxAltOptions, options.size()));
            options.subList(n.maxAltOptions, options.size())
                .clear();
            complete = false;
        }
        if (skipped > 0) {
            notes.add(new Note(SolverMessage.STOPPED_EARLY, evaluated, skipped));
        }
        return new Alternatives(chosen, List.copyOf(options), complete, List.copyOf(notes));
    }

    /** A candidate support reached by closing one gate and opening another, with its witness. */
    private record Swap(int out, int in, StageOutcome witness) {}

    /** The same candidates, dealt out one per decision per round, best-first within each. */
    private static List<Swap> interleaveByDecision(final List<Swap> sorted) {
        final Map<Integer, List<Swap>> byDecision = new LinkedHashMap<>();
        for (final Swap swap : sorted) {
            byDecision.computeIfAbsent(swap.out(), k -> new ArrayList<>())
                .add(swap);
        }
        final List<Swap> out = new ArrayList<>(sorted.size());
        for (int round = 0; out.size() < sorted.size(); round++) {
            for (final List<Swap> queue : byDecision.values()) {
                if (round < queue.size()) out.add(queue.get(round));
            }
        }
        return out;
    }

    /**
     * Applies a stored choice, or explains in a note why it could not be. Returns the alternative
     * to commit, or the incumbent point unchanged when the choice no longer fits or already IS the
     * answer (the notes say so) - never null.
     */
    public static StageOutcome applyChoice(final SolveContext ctx, final ChoiceKey choice) {
        final Set<Integer> target = resolve(ctx, choice);
        if (target == null) {
            ctx.notes.add(new Note(SolverMessage.CHOICE_NO_LONGER_FITS));
            return ctx.point();
        }
        if (target.equals(ctx.support())) return ctx.point();

        final StageOutcome alt = evaluateSupport(ctx, target);
        if (alt == null || alt.support.size() != ctx.support()
            .size()) {
            // Gate count is the one bar a choice may not fall below: it is a real optimum, where
            // everything after it is a preference the user is entitled to disagree with.
            ctx.notes.add(new Note(SolverMessage.CHOICE_NEEDS_MORE_GATES));
            return ctx.point();
        }
        // Deliberately no note when the pick leans on the outside more than the default would have:
        // that is what choosing a listed alternative MEANS, and the row offering it already says so.
        return alt;
    }

    /** Whether a stored {@link ChoiceKey} still names a support on this chart, or null when it does not. */
    public static @Nullable Set<Integer> resolve(final SolveContext ctx, final ChoiceKey choice) {
        final Set<Integer> support = new HashSet<>();
        for (final PortRef anchor : choice.gateAnchors()) {
            final Integer machine = ctx.model.machineIndex.get(anchor.nodeId());
            if (machine == null) return null;
            final long key = ((long) machine << 32) | ((long) anchor.portIndex() << 1) | (anchor.input() ? 1 : 0);
            final Integer port = ctx.model.portLookup.get(key);
            // Not a ternary: mixing int and Integer in one makes javac unbox both arms, so a
            // null from the ingredient fallback becomes an NPE instead of "no match".
            Integer gate = null;
            if (port != null) {
                gate = ctx.model.portGate[port];
            } else {
                gate = resolveByIngredient(ctx, anchor);
            }
            if (gate == null) return null;
            // Two anchors landing on one gate means the key no longer describes the support it was
            // written for: an edge has merged two ingredient components since it was stored.
            if (!support.add(gate)) return null;
        }
        return support;
    }

    /** The gate matching a stored anchor by ingredient, only when the match is unique. */
    private static Integer resolveByIngredient(final SolveContext ctx, final PortRef anchor) {
        final ModelData model = ctx.model;
        final ModelData.Machine machine = model.machines.get(model.machineIndex.get(anchor.nodeId()));
        final var ports = anchor.input() ? machine.node()
            .getInputs()
            : machine.node()
                .getOutputs();
        if (anchor.portIndex() < 0 || anchor.portIndex() >= ports.size()) return null;
        final var want = ports.get(anchor.portIndex());
        Integer found = null;
        for (int p = 0; p < model.connectedPorts.size(); p++) {
            final ModelData.ConnectedPort cp = model.connectedPorts.get(p);
            if (cp.input() != anchor.input()) continue;
            final var other = model.machines.get(cp.machine())
                .node();
            final var candidate = (cp.input() ? other.getInputs() : other.getOutputs()).get(cp.portIndex());
            if (!want.canConnect(candidate)) continue;
            if (found != null && found != model.portGate[p]) return null; // ambiguous, so no answer
            found = model.portGate[p];
        }
        return found;
    }

    /** The stage-2 -> stage-3 -> canonical point over one fixed gate support, or null. */
    public static StageOutcome evaluateSupport(final SolveContext ctx, final Set<Integer> open) {
        final SolveResult s2Result = Solver.fixedQuantity(ctx, open);
        if (s2Result.isRejected()) return null;
        final StageOutcome s2 = s2Result.point();
        final Set<Integer> carrying = ctx.carryingGates(s2.externals, s2.support);
        final SolveResult s3Result = Solver.flowMinimal(ctx, carrying, s2.externalQuantity);
        if (s3Result.isRejected()) return null;
        return Solver.canonicalize(ctx, carrying, s3Result.point());
    }

    /** One answer packaged for the UI, carrying only the flows at the gate it opens. */
    private static Alternative asAlternative(final SolveContext ctx, final double[] externals,
        final Set<Integer> support, final int replaced, final int opened, final Rank rank) {
        final double dust = ctx.heuristics.numerics().dust * SolveContext.scaleOf(externals);
        final List<External> flows = new ArrayList<>();
        for (final int p : ctx.model.gates.get(opened)
            .ports()) {
            if (externals[p] <= dust) continue;
            flows.add(new External(ctx.refOf(p), externals[p]));
        }
        return new Alternative(
            ctx.keyOf(support),
            ctx.anchorOf(replaced),
            ctx.anchorOf(opened),
            List.copyOf(flows),
            rank);
    }

    private static double sum(final double[] values) {
        double total = 0;
        for (final double v : values) {
            total += v;
        }
        return total;
    }

    /** Gate indices in a stable order, so the swap search is reproducible. */
    private static List<Integer> sorted(final Set<Integer> gates) {
        final List<Integer> out = new ArrayList<>(gates);
        out.sort(Comparator.naturalOrder());
        return out;
    }
}
