package com.sbancuz.plannh.gui;

/**
 * Pin placement, the single authority for everything that draws or measures ports: pin
 * {@code i} sits at {@code y = (i + 1) * SPACING + ORIGIN} on its node edge. Deliberately
 * Minecraft-free so headless code (AutoLayout) can share it.
 */
public final class PortGeometry {

    public static final int SPACING = 18;
    public static final int ORIGIN = 10;

    private PortGeometry() {}

    /** World-space Y of a port relative to its node's top edge. */
    public static int portY(final int index) {
        return (index + 1) * SPACING + ORIGIN;
    }
}
