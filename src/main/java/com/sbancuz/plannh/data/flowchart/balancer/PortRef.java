package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.Comparator;
import java.util.UUID;

/**
 * A port on a specific machine. {@code input} distinguishes the two port lists. Used as a
 * portable identity: it names positions in the node, not indices rebuilt per solve, so it survives
 * a save/reload. Ordering and equality must not change (the Serializer and the choices panel
 * depend on them).
 */
public record PortRef(UUID nodeId, int portIndex, boolean input) {

    /**
     * Canonical order, matching the order machines are built in, so a tie is broken by the chart
     * itself rather than by the order branch-and-bound enumerated in.
     */
    public static final Comparator<PortRef> ORDER = Comparator.comparing(PortRef::nodeId)
        .thenComparing(PortRef::input)
        .thenComparingInt(PortRef::portIndex);
}
