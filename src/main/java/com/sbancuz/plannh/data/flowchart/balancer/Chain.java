package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A balancer solve expressed as a typed graph: preludes, then an entry, then stages, with an
 * optional between-pass audit. The payload type parameter tracks what the entry produced through
 * every {@code then} - the frontier types line up or the chain does not compile - while the runtime
 * only ever sees the erased {@code Stem} list with its single fenced cast (the one place an
 * unchecked {@code (IN) carried} happens).
 *
 * <p>
 * {@link #run(SolveContext)} drives the chain pass by pass: pass one floor-free, then the audits'
 * floors when machines were left idle, then a second floored pass. Guards: a stage that
 * {@link Outcome.Fail fails} stops the run and is reported with the stage name as prefix; a
 * prelude that commits and short-circuits halts the chain (the zero-gate fast path); and on the
 * AUTO chain - the one whose permissive model makes the question meaningful - a stalled pass one
 * consults {@link SolveContext#diagnosePins()} before the run gives up, so a pin conflict is named
 * rather than reported as a cryptic stage failure.
 */
public final class Chain<P> {

    /** The erased shape every chain element reduces to: a name and a payload-in/payload-out run. */
    private interface Stem {

        String name();

        Outcome<?> run(SolveContext ctx, Object carried);
    }

    /** A pass's result: the settlement it produced and whether a prelude short-circuited it. */
    private record PassOutcome(Settlement settlement, boolean fastPath) {}

    private record EntryErased<OUT> (Entry<OUT> entry) implements Stem {

        @Override
        public String name() {
            return entry.name();
        }

        @Override
        public Outcome<?> run(final SolveContext ctx, final Object carried) {
            return entry.run(ctx);
        }
    }

    private record StageErased<IN, OUT> (Stage<IN, OUT> stage) implements Stem {

        @Override
        public String name() {
            return stage.name();
        }

        @Override
        public Outcome<?> run(final SolveContext ctx, final Object carried) {
            return stage.run(ctx, (IN) carried); // the ONE cast, fenced here
        }
    }

    private final List<Stem> stems;
    private final List<Prelude> preludes;
    private final List<StageAudit> audits;
    /** True when a stalled pass one should name a pin conflict; the AUTO chain is the one that can. */
    private final boolean diagnosePins;

    private Chain(final List<Stem> stems, final List<Prelude> preludes, final List<StageAudit> audits,
        final boolean diagnosePins) {
        this.stems = List.copyOf(stems);
        this.preludes = List.copyOf(preludes);
        this.audits = List.copyOf(audits);
        this.diagnosePins = diagnosePins;
    }

    /** The empty chain, for a mode that never solves (NONE); never run by {@link Balancer}. */
    public static Chain<Void> empty() {
        return new Chain<>(List.of(), List.of(), List.of(), false);
    }

    /** Starts a chain with its entry: the element that consumes nothing and seeds the payload. */
    public static <OUT> Chain<OUT> enter(final Entry<OUT> entry) {
        return new Chain<>(List.of(new EntryErased<>(entry)), List.of(), List.of(), false);
    }

    /** Appends a stage consuming {@code P} and producing {@code Q}; the types line up or this does not compile. */
    public <Q> Chain<Q> then(final Stage<P, Q> stage) {
        final List<Stem> next = new ArrayList<>(stems);
        next.add(new StageErased<>(stage));
        return new Chain<>(next, preludes, audits, diagnosePins);
    }

    /** Adds a prelude, which runs before the entry and may short-circuit the chain. */
    public Chain<P> prelude(final Prelude prelude) {
        final List<Prelude> next = new ArrayList<>(preludes);
        next.add(prelude);
        return new Chain<>(stems, next, audits, diagnosePins);
    }

    /** Adds a between-pass floor gate, consulted after pass one and given its own re-run. */
    public Chain<P> audit(final StageAudit audit) {
        final List<StageAudit> next = new ArrayList<>(audits);
        next.add(audit);
        return new Chain<>(stems, preludes, next, diagnosePins);
    }

    /** Marks the chain to name a pin conflict when a floor-free pass one stalls (the AUTO chain). */
    public Chain<P> withPinDiagnosis() {
        return new Chain<>(stems, preludes, audits, true);
    }

    /**
     * Runs the whole chain: pass one floor-free, then the audit floors when machines were left
     * idle, then a second floored pass.
     */
    public Settlement run(final SolveContext ctx) {
        final PassOutcome first = pass(ctx);
        if (first.settlement() instanceof Settlement.Stalled(Note reason)) {
            if (!diagnosePins) {
                return new Settlement.Stalled(SolverMessage.BALANCE_FAILED.toNote(reason));
            }
            // A floor-free pass 1 fails only when the most permissive model is infeasible; with
            // nonnegative externals on every connected port the only thing left to conflict is the
            // pins. Say so rather than submitting a cryptic "gate count ...".
            final Note conflict = ctx.diagnosePins();
            return conflict == null
                ? new Settlement.Stalled(SolverMessage.BALANCE_FAILED.toNote(reason))
                : new Settlement.Stalled(
                    SolverMessage.BALANCE_FAILED.toNote(SolverMessage.STAGE_FAILED.toNote(reason, conflict)));
        }
        if (first.fastPath()) {
            ctx.profiler.fastPath();
        }
        // The fast path may commit a point, but the audits consult it all the same: the replay is
        // a contract with every outcome, fast path included. Only the chain itself halts early.
        double[] floors = new double[0];
        for (final StageAudit audit : audits) {
            floors = audit.floorsFor(ctx);
            if (floors.length > 0) break;
        }
        if (floors.length == 0 || ctx.point() == null) {
            return first.settlement();
        }
        final int idleBefore = ctx.idleCount(ctx.extents());
        ctx.resetForReplay();
        ctx.floors = floors;
        ctx.profiler.floorReplay(idleBefore);
        final Settlement second = pass(ctx).settlement();
        if (second instanceof final Settlement.Stalled stalled) {
            return new Settlement.Stalled(SolverMessage.MACHINES_CANNOT_RUN.toNote(idleBefore, stalled.reason()));
        }
        ctx.floorsUsed = true;
        return second;
    }

    private PassOutcome pass(final SolveContext ctx) {
        final Profiler profiler = ctx.profiler;
        final boolean instrumented = profiler.enabled();
        ctx.stageNotes.clear();
        for (final Prelude prelude : preludes) {
            if (ctx.shortCircuit) break;
            final long start = instrumented ? System.currentTimeMillis() : 0;
            if (instrumented) profiler.stageStarted(prelude.name());
            prelude.run(ctx);
            if (instrumented) {
                // A prelude may leave the point where it found it; only a point this prelude
                // actually committed is its witness.
                final StageOutcome point = ctx.point();
                profiler.stageFinished(
                    prelude.name(),
                    point == null ? Optional.empty() : Optional.of(StageOutcome.snapshot(ctx)),
                    System.currentTimeMillis() - start);
            }
        }
        // Only the prelude phase can be a "fast path": a prelude that commits and halts the chain
        // IS the zero-gate fast path, while an entry or stage short-circuit is a full solve.
        final boolean fastPath = ctx.shortCircuit;
        Object carried = null;
        for (final Stem stem : stems) {
            if (ctx.shortCircuit) break;
            final long start = instrumented ? System.currentTimeMillis() : 0;
            if (instrumented) profiler.stageStarted(stem.name());
            final Outcome<?> outcome = stem.run(ctx, carried);
            if (outcome instanceof Outcome.Fail<?>(Note reason)) {
                if (instrumented) {
                    profiler.stageFinished(stem.name(), Optional.empty(), System.currentTimeMillis() - start);
                }
                return new PassOutcome(new Settlement.Stalled(SolverMessage.STAGE_FAILED.toNote(stem.name(), reason)),
                    fastPath);
            }
            carried = ((Outcome.Continue<?>) outcome).value();
            if (instrumented) {
                final StageOutcome point = ctx.point();
                profiler.stageFinished(
                    stem.name(),
                    point == null ? Optional.empty() : Optional.of(StageOutcome.snapshot(ctx)),
                    System.currentTimeMillis() - start);
            }
        }
        final StageOutcome point = ctx.point();
        return point == null
            ? new PassOutcome(new Settlement.Stalled(SolverMessage.BALANCE_FAILED.toNote(ctx.rejection)), fastPath)
            : new PassOutcome(new Settlement.Solved(point, settleNotes(ctx)), fastPath);
    }

    private static List<Note> settleNotes(final SolveContext ctx) {
        final List<Note> all = new ArrayList<>(ctx.notes);
        all.addAll(ctx.stageNotes);
        return List.copyOf(all);
    }
}
