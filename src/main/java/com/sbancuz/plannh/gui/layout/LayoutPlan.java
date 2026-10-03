package com.sbancuz.plannh.gui.layout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record LayoutPlan(Map<UUID, Point> machines, Map<UUID, Box> groupFrames, Map<UUID, Point> notes) {

    public LayoutPlan {
        machines = Collections.unmodifiableMap(new LinkedHashMap<>(machines));
        groupFrames = Collections.unmodifiableMap(new LinkedHashMap<>(groupFrames));
        notes = Collections.unmodifiableMap(new LinkedHashMap<>(notes));
    }

    /** Nothing to do: no machines, no groups, no notes. */
    public static LayoutPlan empty() {
        return new LayoutPlan(Map.of(), Map.of(), Map.of());
    }

    /**
     * Whether this plan places anything at all. A failed layout returns this, so a caller can bail before editing
     * anything.
     */
    public boolean isEmpty() {
        return machines.isEmpty() && groupFrames.isEmpty() && notes.isEmpty();
    }
}
