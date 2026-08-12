package com.sbancuz.plannh.data.flowchart.balancer;

/**
 * Which pin kinds a balancer type may honour. The pin-availability is a per-type concern:
 * OUTPUT/INPUT honour only fixed machine counts, AUTO also honours target-rate (extent) pins, NONE
 * honours none. A type that does not include a pin kind ignores it entirely when building its
 * solve context - so an extent pin on the chart is invisible to a type that only knows counts.
 */
public enum Pin {

    /** The node's fixed machine count, converted to an extent (count * ticks/s / duration). */
    FIXED_COUNT,

    /** A node's target output rate, converted to the extent that just satisfies it. */
    TARGET_RATE,

    /** An explicit extra extent pin supplied by the caller (crafts/second). */
    EXTENT
}
