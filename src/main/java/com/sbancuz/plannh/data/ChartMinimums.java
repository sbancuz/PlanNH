package com.sbancuz.plannh.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Plan;

/**
 * The settings a chart can set a floor for, registered by the installed mods.
 *
 * <p>
 * A floor is stored on the chart, not on each node. A chart is a factory at one point in a world's
 * progression, so per-node floors would all store the same coil limit. Only the mod that owns the
 * machine registers which settings take a floor. PlanNH has no coil concept, and the panel that draws these
 * rows must run on a pack without GregTech.
 *
 * <p>
 * Static like {@link MachineProfileRegistry} and {@code RecipePropertyAPI}, and reset with them from
 * {@code Compat.init}, because providers register into it during the same pass.
 */
public final class ChartMinimums {

    private ChartMinimums() {}

    /**
     * One floor row on the chart.
     *
     * @param setting the setting this is a floor for, also its key on the chart
     * @param label   row label, short enough to fit beside its two steppers
     * @param best    value an untouched chart is planned at. Build through {@link Minimum#strongest} or
     *                {@link Minimum#weakest}: the default end follows from the kind of setting
     * @param name    display name of a tier: the block a player places, not the stored number
     */
    public record Minimum(Settings setting, String label, int min, int max, int best, IntFunction<String> name) {

        /**
         * A setting that gates progress, such as a coil or a capacitor. An untouched chart is planned at
         * the best tier the pack ships, since a player who has reached this chart can build it.
         */
        @Nonnull
        public static Minimum strongest(final Settings setting, final String label, final int min, final int max,
            final IntFunction<String> name) {
            return new Minimum(setting, label, min, max, max, name);
        }

        /**
         * A setting that adds cost without unlocking anything. A higher floor makes every recipe more
         * expensive, so an untouched chart is planned at the cheapest end.
         */
        @Nonnull
        public static Minimum weakest(final Settings setting, final String label, final int min, final int max,
            final IntFunction<String> name) {
            return new Minimum(setting, label, min, max, min, name);
        }

        /** The floor stored on this chart, or {@link #best} when none is set. */
        public int current(@Nonnull final Graph graph) {
            final int held = graph.getMinimum(setting.key());
            return held == Graph.NO_MINIMUM ? best : held;
        }

        /**
         * Steps the chart's floor, never past either end. The unset marker is not reachable: it means
         * "whatever the game allows", which is already a value in the range, so a separate step for it
         * would change nothing visible.
         */
        public void step(@Nonnull final Graph graph, final int by) {
            graph.setMinimum(setting.key(), Math.max(min, Math.min(max, current(graph) + by)));
        }
    }

    private static final List<Minimum> registered = new ArrayList<>();

    public static void register(@Nonnull final Minimum minimum) {
        registered.add(minimum);
    }

    /** In registration order, so each mod's rows appear in the order that mod registered them. */
    @Nonnull
    public static List<Minimum> all() {
        return List.copyOf(registered);
    }

    public static void reset() {
        registered.clear();
    }

    /**
     * The floor the open chart sets for one setting, or {@code best} when none is set.
     *
     * <p>
     * Read from the chart on screen, not passed in: a {@link SettingDef} receives the recipe and the node's
     * settings, never the node or its graph, and only the active chart draws rows. Reaching the open plan
     * loads Minecraft through the save directory, so outside a running game this returns {@code best}, which
     * headless tests rely on.
     */
    public static int floor(@Nonnull final Settings setting, final int best) {
        final int held;
        try {
            held = Plan.getActiveGraph()
                .getMinimum(setting.key());
        } catch (final RuntimeException | LinkageError outsideAGame) {
            return best;
        }
        return held == Graph.NO_MINIMUM ? best : held;
    }
}
