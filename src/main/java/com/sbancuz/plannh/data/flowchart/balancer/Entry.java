package com.sbancuz.plannh.data.flowchart.balancer;

/**
 * The first solving element of a {@link Chain}: it consumes nothing and seeds the payload every
 * later stage is handed. A single-extent balancer (OUTPUT / INPUT) is a chain of exactly one entry;
 * the AUTO chain's entry is the gate-count search.
 *
 * @param <OUT> the payload type handed to the next chain element
 */
public interface Entry<OUT> extends ChainElement {

    /** Execute this entry against the shared, zero-copy context. */
    Outcome<OUT> run(SolveContext ctx);
}
