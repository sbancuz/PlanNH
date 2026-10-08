package com.sbancuz.plannh.data.flowchart.balancer;

/**
 * One step of a balancer {@link Chain} past the entry. A chain is a typed composition of these, so
 * defining a new balancer means choosing stages, not editing one shared class.
 *
 * <p>
 * Stages act on the shared {@link SolveContext}: they read the build-once {@link ModelData}, the
 * heuristics and the caps already posted, consume the payload handed in, and - on a problem the
 * stage cannot move past - return {@link Outcome.Fail} so the chain stops. A stage that finds
 * nothing for itself is not a failure; it hands the next stage its payload (or a "nothing changed"
 * one) and the next stage simply decides.
 *
 * @param <IN>  the payload type this stage consumes (what the previous element produced)
 * @param <OUT> the payload type this stage produces for the next element
 */
public interface Stage<IN, OUT> extends ChainElement {

    /** Execute this stage against the shared, zero-copy context and the carried payload. */
    Outcome<OUT> run(SolveContext ctx, IN carried);
}
