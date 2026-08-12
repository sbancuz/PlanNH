package com.sbancuz.plannh.data.flowchart.balancer;

/**
 * How loudly a solver note speaks. Carried by {@link SolverMessage} and reached through
 * {@link Note#severity()}, which is what colours the "Solver Messages" bar.
 */
public enum Severity {

    /** The chart has no numbers: nothing was solved. */
    ERROR,
    /** Solved, but on something the user probably did not mean - a pin missed, an edge absent. */
    WARN,
    /** The solver saying what it did. Nothing to fix. */
    INFO;
}
