package com.sbancuz.plannh.data.flowchart.balancer;

/**
 * The common shape of every element of a {@link Chain} - an {@link Entry}, a {@link Stage} or a
 * {@link Prelude}: each has a name used in failures ("gate count ...") and profiler events.
 */
public interface ChainElement {

    /** The element's name; used in failures and profiler events. */
    default String name() {
        return getClass().getSimpleName();
    }
}
