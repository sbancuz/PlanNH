package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The identity of one answer: the anchor port of every open gate, sorted. Ports and not gate
 * indices, because gate indices are rebuilt on every solve and do not survive a save. The anchor is
 * the smallest {@link PortRef} among ALL of a gate's ports, not only the ones carrying flow, so the
 * key does not move when the solver redistributes a dump between two ports of one gate.
 */
public record ChoiceKey(List<PortRef> gateAnchors) implements Comparable<ChoiceKey> {

    public static ChoiceKey of(final Collection<PortRef> anchors) {
        final List<PortRef> sorted = new ArrayList<>(anchors);
        sorted.sort(PortRef.ORDER);
        return new ChoiceKey(List.copyOf(sorted));
    }

    @Override
    public int compareTo(final ChoiceKey other) {
        final int n = Math.min(gateAnchors.size(), other.gateAnchors.size());
        for (int i = 0; i < n; i++) {
            final int cmp = PortRef.ORDER.compare(gateAnchors.get(i), other.gateAnchors.get(i));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(gateAnchors.size(), other.gateAnchors.size());
    }

    public static ChoiceKey none() {
        return ChoiceKey.of(new ArrayList<>());
    }
}
