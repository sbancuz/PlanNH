package com.sbancuz.plannh.data.setting;

/**
 * How a settings widget commits a value the player typed, toggled or cycled.
 *
 * <p>
 * A widget holds the value and the config, but not the chart the setting belongs to - and a setting
 * is a solve input, not a widget-local preference: the machine count, the overclock tier and the
 * heat all decide what the balancer comes back with. So the panel owns the consequence and the
 * widget hands over the change itself: the panel records it (so undo reaches it), re-solves, saves,
 * and rebuilds the rows, because a value can decide what else is on offer.
 */
@FunctionalInterface
public interface SettingEdit {

    /** Runs the value change as a chart edit, then re-solves, saves and refreshes the rows. */
    void apply(Runnable change);
}
