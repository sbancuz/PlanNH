package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.Objects;

/**
 * What one chain element leaves behind: a payload for the next element, or the reason it stalled.
 * A stage that finds nothing for itself is not a failure - it hands the {@link Continue} payload
 * it received, or a payload that says "nothing changed" - so the next element simply decides. The
 * only null in the pipeline is the context's point before anything commits; everything a stage
 * RETURNS is one of these two defined states.
 *
 * @param <OUT> the payload type handed to the next chain element
 */
public sealed interface Outcome<OUT> permits Outcome.Continue,Outcome.Fail {

    /** The element advanced the chain; {@code value} is handed to the next element. */
    record Continue<OUT> (OUT value) implements Outcome<OUT> {

        public Continue {
            Objects.requireNonNull(value);
        }
    }

    /** The element cannot move the chain past this problem; {@code reason} is why. */
    record Fail<OUT> (Note reason) implements Outcome<OUT> {}
}
