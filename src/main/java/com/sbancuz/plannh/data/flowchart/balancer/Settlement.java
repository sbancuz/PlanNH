package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.List;

/**
 * The explicit outcome of one {@link Chain#run(SolveContext)}: either a usable point or the reason
 * there is none. A solve is always one of the two defined shapes, so a caller pattern-matches
 * instead of null-checking.
 */
public sealed interface Settlement permits Settlement.Solved,Settlement.Stalled {

    /** The chain committed a point; {@code notes} are the run's final answer notes. */
    record Solved(StageOutcome point, List<Note> notes) implements Settlement {

        public Solved {
            notes = List.copyOf(notes);
        }
    }

    /** The chain could not produce a point; {@code reason} is why. */
    record Stalled(Note reason) implements Settlement {}
}
