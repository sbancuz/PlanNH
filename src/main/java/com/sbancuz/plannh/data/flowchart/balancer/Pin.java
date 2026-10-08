package com.sbancuz.plannh.data.flowchart.balancer;

/**
 * Which pin kinds a balancer type may honour. The pin-availability is a per-type concern:
 * OUTPUT/INPUT honour only fixed machine counts, AUTO also honours target-rate (extent) pins, NONE
 * honours none. A type that does not include a pin kind ignores it entirely when building its
 * solve context - so an extent pin on the chart is invisible to a type that only knows counts.
 */
public enum Pin {

    NONE("plannh.solver.none", true),

    /** The node's fixed copy count, converted to an extent (copies * ticks/s / duration). */
    FIXED_COPIES("plannh.solver.pin_copies", true),

    /** A node's target output rate, converted to the extent that just satisfies it. */
    TARGET_RATE("plannh.solver.pin_target", true),

    /** An explicit extra extent pin supplied by the caller (crafts/second). */
    EXTENT("plannh.solver.pin_extent", false);

    private final String key;
    public final boolean hasWidget;

    public static final Pin[] VALUES = values();

    Pin(final String langKey, boolean hasWidget) {
        this.key = langKey;
        this.hasWidget = hasWidget;
    }

    public String key() {
        return key;
    }

}
