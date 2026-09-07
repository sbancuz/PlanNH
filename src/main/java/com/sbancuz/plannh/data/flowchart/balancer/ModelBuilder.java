package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.function.IntToDoubleFunction;

import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;
import org.ojalgo.optimisation.integer.IntegerStrategy;
import org.ojalgo.type.context.NumberContext;

/**
 * The ONE place an LP model is assembled. A stage declares what it wants - which variable
 * families, which row families, which objective - as a fluent chain over the build-once
 * {@link ModelData}, and {@link #solve(String)} hands back the ojAlgo result. Composable steps, so
 * a new balancer type expresses its constraint family in its stage instead of reaching into Solver
 * internals.
 *
 * <p>
 * A model is a fresh ojAlgo object (inherent to the library, allowed by the zero-copy rule) while
 * everything it reads - machines, ports, edges, gates - lives once on {@link ModelData}. The flags
 * below are the guard rails: chaining {@code portRows(...)} before {@code flows()} is a caller
 * bug, and the builder says so rather than indexing past an empty array.
 *
 * <p>
 * Construction order matters only in the trivial sense that a row references the variables before
 * it, so each family must be declared before the row that uses it:
 * {@code extents(..).flows().externals().conservation()} or
 * {@code extents(..).flows().portRows(..)}. The objective and the model options may be set at any
 * point before {@link #solve(String)}.
 */
public final class ModelBuilder {

    private final SolveContext ctx;
    private final ExpressionsBasedModel m = new ExpressionsBasedModel();

    private Variable[] extentVars = new Variable[0];
    private Variable[] flowVars = new Variable[0];
    private Variable[] extVars = new Variable[0];
    private Variable[] gateVars = new Variable[0];
    private boolean extents;
    private boolean flows;
    private boolean externals;
    private boolean countsSpace;

    private ModelBuilder(final SolveContext ctx) {
        this.ctx = ctx;
        final Numerics numerics = ctx.heuristics.numerics();
        // Never longer than what the whole solve has left. The floor matters as much as the
        // ceiling: a model handed a millisecond aborts into whatever point it is holding.
        final long limit = Math
            .clamp(numerics.effort(numerics.stageTimeLimitMillis), numerics.minModelMillis, ctx.budget.remaining());
        m.options.time_abort = limit;
        m.options.time_suffice = limit;
        // One branch-and-bound worker, so the node order is a property of the model, not of thread
        // interleaving, and the same chart answers the same on every machine.
        m.options.integer(
            IntegerStrategy.DEFAULT.withGapTolerance(NumberContext.of(12, 8))
                .withParallelism(() -> 1));
        m.options.feasibility = NumberContext.of(12, 10);
    }

    /** Starts a fresh model over the shared chart data; the stage factors that choose its parts. */
    public static ModelBuilder over(final SolveContext ctx) {
        return new ModelBuilder(ctx);
    }

    /**
     * One extent (crafts/s) variable per machine. Pinned machines are locked at their pin; the
     * rest floor at {@code lower[m]} - the AUTO stages pass the stage-0 floors or zeros, the
     * OUTPUT / INPUT stage the one-machine floor {@code TICKS_PER_SECOND/durTicks}.
     */
    public ModelBuilder extents(final double[] lower) {
        final ModelData model = ctx.model;
        extentVars = new Variable[model.machines.size()];
        for (int i = 0; i < extentVars.length; i++) {
            final Variable v = m.addVariable("extent_" + i);
            final double pinned = ctx.pinnedExtent[i];
            if (!Double.isNaN(pinned)) {
                v.lower(pinned)
                    .upper(pinned);
            } else {
                v.lower(lower[i]);
            }
            extentVars[i] = v;
        }
        extents = true;
        return this;
    }

    /**
     * One integer machine-count variable per machine, replacing the continuous extents family. The
     * OUTPUT / INPUT stage solves in count space so its answer is buildable without a read-out
     * ceil: the variable IS the machine count (a MILP rather than a bare LP). Pinned machines lock
     * at their configured count (never fractional in these modes - only FIXED_COUNT pins are
     * honoured); everything else floors at one machine, the count-world image of the one-machine
     * extent floor. Port rows and the point reader convert back through TPS/durTicks.
     */
    public ModelBuilder extentCounts() {
        final ModelData model = ctx.model;
        extentVars = new Variable[model.machines.size()];
        for (int i = 0; i < extentVars.length; i++) {
            final Variable v = m.addVariable("count_" + i)
                .integer();
            final double pinned = ctx.pinnedExtent[i];
            if (!Double.isNaN(pinned)) {
                final double count = Math
                    .round(pinned * model.machines.get(i).durTicks / (double) Numerics.TICKS_PER_SECOND);
                v.lower(count)
                    .upper(count);
            } else {
                v.lower(1.0);
            }
            extentVars[i] = v;
        }
        extents = true;
        countsSpace = true;
        return this;
    }

    /**
     * One capacity row per machine-sharing pool: the machines framed by a capped group may spend
     * no more machine time between them than the group has machines. A machine's own time is its
     * extent times the seconds one craft takes, so the row is {@code Σ extent_i * durTicks_i/TPS <=
     * capacity} - the same number the group header shows, held as a constraint instead of read off
     * afterwards. In count space the variable already IS machine time, so the coefficient is 1.
     *
     * <p>
     * Nothing is added when the chart has no capped group, which is every chart that never touched
     * the feature. Requires {@link #extents(double[])} or {@link #extentCounts()}.
     */
    public ModelBuilder pools() {
        require(extents, "extents() or extentCounts()");
        final ModelData model = ctx.model;
        for (int p = 0; p < model.pools.size(); p++) {
            final ModelData.Pool pool = model.pools.get(p);
            final Expression row = m.addExpression("pool_" + p);
            for (final int machine : pool.machines()) {
                row.set(
                    extentVars[machine],
                    countsSpace ? 1.0 : model.machines.get(machine).durTicks / (double) Numerics.TICKS_PER_SECOND);
            }
            row.upper(pool.capacity());
        }
        return this;
    }

    /** One nonnegative flow (items/s) variable per drawn edge. */
    public ModelBuilder flows() {
        final ModelData model = ctx.model;
        flowVars = new Variable[model.edges.size()];
        for (int e = 0; e < flowVars.length; e++) {
            flowVars[e] = m.addVariable("flow_" + e)
                .lower(0);
        }
        flows = true;
        return this;
    }

    /** One nonnegative external variable per connected port. */
    public ModelBuilder externals() {
        extVars = new Variable[ctx.model.connectedPorts.size()];
        for (int p = 0; p < extVars.length; p++) {
            extVars[p] = m.addVariable("ext_" + p)
                .lower(0);
        }
        externals = true;
        return this;
    }

    /** One binary gate variable per gate, each linked to its ports' externals by a big-M row. */
    public ModelBuilder gates(final double bigM) {
        requireExternals();
        final ModelData model = ctx.model;
        gateVars = new Variable[model.gates.size()];
        for (int g = 0; g < gateVars.length; g++) {
            gateVars[g] = m.addVariable("y_" + g)
                .binary();
            final Expression link = m.addExpression("link_" + g);
            for (final int p : model.gates.get(g)
                .ports()) {
                link.set(extVars[p], 1.0);
            }
            link.set(gateVars[g], -bigM);
            link.upper(0);
        }
        return this;
    }

    /**
     * The AUTO conservation rows: per connected port, the drawn flows plus the external minus the
     * machine's own rate balance to zero, scaled by {@code 1/max(1, qty)} so coefficients stay
     * near 1. Requires {@link #extents(double[])}, {@link #flows()} and {@link #externals()}.
     */
    public ModelBuilder conservation() {
        require(extents && flows && externals, "extents(), flows() and externals()");
        final ModelData model = ctx.model;
        for (int p = 0; p < model.connectedPorts.size(); p++) {
            final ModelData.ConnectedPort port = model.connectedPorts.get(p);
            final double scale = 1.0 / Math.max(1.0, port.qtyPerCraft());
            final Expression row = m.addExpression("port_" + p);
            for (final int e : port.edges()) {
                row.set(flowVars[e], scale);
            }
            row.set(extVars[p], scale);
            row.set(extentVars[port.machine()], -port.qtyPerCraft() * scale * extentFactor(port.machine()));
            row.level(0);
        }
        return this;
    }

    /**
     * The OUTPUT / INPUT port rows (the fold of the OUTPUT / INPUT balance): the priority side is
     * claimed exactly (extent's own production shipped / consumed on the drawn edges), the other
     * side runs at-least, and a port with no capacity or no edges is skipped entirely. When
     * {@link #externals()} is declared the at-least rows also carry an import variable (the
     * external on the SAME port, same scale), so the non-priority side may pull the shortfall the
     * drawn edges cannot cover from outside - the desulfurization loop's distillation tower gives
     * the chemical reactor 500/s but it needs 1500/s, and the missing 1000/s is imported rather
     * than made infeasible. The claimed rows stay extern-free: an exact claim cannot be met by
     * buying the ingredient. Requires {@link #extents(double[])} and {@link #flows()}.
     *
     * @param inputPriority true when PRIORITY is inputs exact / outputs at-least (INPUT mode).
     */
    public ModelBuilder portRows(final boolean inputPriority) {
        require(extents && flows, "extents() and flows()");
        final ModelData model = ctx.model;
        for (int p = 0; p < model.connectedPorts.size(); p++) {
            final ModelData.ConnectedPort port = model.connectedPorts.get(p);
            if (port.qtyPerCraft() <= 0 || port.edges()
                .isEmpty()) continue;
            final boolean claimed = inputPriority == port.input();
            final double scale = 1.0 / Math.max(1.0, port.qtyPerCraft());
            final Expression row = m.addExpression("port_" + p);
            for (final int e : port.edges()) {
                row.set(flowVars[e], scale);
            }
            if (!claimed && externals) {
                row.set(extVars[p], scale);
            }
            row.set(extentVars[port.machine()], -port.qtyPerCraft() * scale * extentFactor(port.machine()));
            if (claimed) {
                row.level(0);
            } else {
                row.lower(0);
            }
        }
        return this;
    }

    /**
     * Prices the import variables (the externals on the at-least rows of {@link #portRows(boolean)})
     * as a tie-break, so a free import cannot sit at an arbitrary vertex. The count objective
     * dominates - one machine weighs {@code 1.0} - and {@code weight} is a small fraction of that,
     * so the model still first uses the fewest machines and then pulls the least it must from
     * outside. Requires {@link #externals()}.
     */
    public ModelBuilder importsWeighted(final double weight) {
        requireExternals();
        for (final Variable ext : extVars) {
            ext.weight(weight);
        }
        return this;
    }

    /**
     * Weights every extent variable. The {@code ExtentMinStage} objective is durTicks/20 in rate
     * space, or 1.0 in count space where the variable already IS the machine count.
     */
    public ModelBuilder extentsWeighted(final IntToDoubleFunction weight) {
        for (int i = 0; i < extentVars.length; i++) {
            extentVars[i].weight(weight.applyAsDouble(i));
        }
        return this;
    }

    /** Caps the branch-and-bound nodes a {@link #solve(String)} spends certifying an optimum. */
    public ModelBuilder nodeBudget(final int nodes) {
        m.options.iterations_abort = nodes;
        m.options.iterations_suffice = nodes;
        return this;
    }

    /** The variable arrays, for the readers and the caps that must be set between build and solve. */
    public Handles handles() {
        return new Handles(m, extentVars, flowVars, extVars, gateVars);
    }

    /** Runs the model, reporting to the profiler with {@code label} when one is attached. */
    public Optimisation.Result solve(final String label) {
        final Profiler profiler = ctx.profiler;
        final long start = profiler.enabled() ? System.currentTimeMillis() : 0;
        final Optimisation.Result result = m.minimise();
        if (profiler.enabled()) {
            profiler.modelSolved(
                label,
                System.currentTimeMillis() - start,
                result.getState()
                    .toString(),
                m.countVariables(),
                m.countExpressions());
        }
        return result;
    }

    /**
     * Count-space port rows and conservation rows are written against the machine variable that
     * holds COUNT, not crafts/s, so their rate coefficient must be scaled up by TPS/durTicks
     * (extent = count * TPS/durTicks). Continuous-extent models scale by 1 and are untouched.
     */
    private double extentFactor(final int machine) {
        return countsSpace ? Numerics.TICKS_PER_SECOND / (double) ctx.model.machines.get(machine).durTicks : 1.0;
    }

    private void requireExternals() {
        require(externals, "externals()");
    }

    private static void require(final boolean declared, final String what) {
        if (!declared) {
            throw new IllegalStateException("ModelBuilder: declare " + what + " before this step");
        }
    }
}
