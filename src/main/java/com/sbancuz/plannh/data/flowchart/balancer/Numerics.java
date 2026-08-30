package com.sbancuz.plannh.data.flowchart.balancer;

import com.sbancuz.plannh.Config;

/**
 * The numeric settings of a solve: feast/sensitivity tolerances, model time limits and node budgets,
 * the big-M growth policy and the effort scaler. These are carried inside {@link Heuristics} so a
 * new balancer type can trade fidelity against time differently without touching any stage code.
 */
public final class Numerics {

    /** Duration ticks per second, used for every extent-to-count conversion. */
    public static final int TICKS_PER_SECOND = 20;

    /**
     * How far above a chart's own scale the big-M gate links sit. Relative because the model is
     * homogeneous: one fixed M is absurd on a chart measured in hundredths of an item. Under-estimates
     * are caught by {@link Solver#pressesCap(Handles, double)} and grown.
     */
    public final double bigMFactor = 1e3;
    public final int maxMGrowths = 3;

    /** Ceiling on the WHOLE solve, scaled by effort. */
    public final long solveBudgetMillis = 20_000;
    /** Floor on any one model's time limit, however little of the budget is left. */
    public final long minModelMillis = 250;
    /**
     * Budget for the exact stage-1 MILP, counted in branch-and-bound nodes, deliberately NOT scaled
     * by effort: every corpus certification closes within 109 nodes.
     */
    public final int milpCertNodeBudget = 512;
    /** Relative tolerance for "two solves found the SAME optimum". */
    public final double tieRel = 1e-6;
    /** Relative slack on the stage-2 quantity cap in stage 3. */
    public final double qtyEps = 1e-7;
    /** How many equally-good supports the stage-3 tie enumeration gets to choose between. */
    public final int maxTiedSupports = 5;
    /** Ceiling on any ONE model's model time, scaled by effort. */
    public final long stageTimeLimitMillis = 15_000;

    /** Wall budget for the WHOLE alternatives search, scaled by effort. */
    public final long altBudgetMillis = 750;
    /** How much of the alternatives budget the broad swap search may spend first. */
    public final long altSearchSharePercent = 50;
    /** Cap on gate swaps tried in the alternatives search, scaled by effort. */
    public final int maxAltSwaps = 1024;
    /** How many answers the alternatives list carries before it stops being a list. */
    public final int maxAltOptions = 8;

    /** Flows below this count as zero when deriving gate support (relative). */
    public final double zero = 1e-6;
    /** Solver dust: what a variable holds when the solver meant zero (relative). */
    public final double dust = 1e-11;
    /** Residual tolerance for the independent conservation check, scaled per row. */
    public final double validateTol = 1e-6;
    /** Fallback pass-2 floor (crafts/s) when no machine ran at all. */
    public final double useEps = 1e-4;
    /** A machine "runs" if its crafts/s exceeds this. */
    public final double useEpsDetect = 1e-7;

    /**
     * Packing floor for the lexicographic gate weights; see {@link Heuristics#packedGateWeights}.
     * Static because the packing formula is scale-free by construction.
     */
    public static final double GATE_WEIGHT_FLOOR = 1024.0;

    public final double gateWeightFloor = GATE_WEIGHT_FLOOR;

    /**
     * How much of a tuned effort number {@link Config#solverEffortPercent} buys. Applied where each
     * number is used rather than folded into the constants, so nothing depends on whether this class
     * initialized before the config loaded. Only numbers that trade time for a better answer are
     * scaled.
     */
    public long effort(final long tuned) {
        return Math.max(1, tuned * Config.solverEffort() / 100);
    }
}
