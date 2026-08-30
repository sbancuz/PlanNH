package com.sbancuz.plannh.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Plan;

/**
 * The settings a chart can set a floor for, contributed by whichever mods are installed.
 *
 * <p>
 * A floor belongs to the chart rather than to each node - a chart describes a factory at one point in
 * a world's progression, so asking every node which coil it may use is asking the same question fifty
 * times. Which settings have a floor at all is a different question, and only the mod that owns the
 * machine can answer it: PlanNH knows nothing about coils, and the panel that draws these rows must
 * keep working on a pack with no GregTech.
 *
 * <p>
 * Static like {@link MachineProfileRegistry} and {@code RecipePropertyAPI}, and reset with them from
 * {@code Compat.init}, because providers register into it during the same pass.
 */
public final class ChartMinimums {

    private ChartMinimums() {}

    /**
     * One row.
     *
     * @param setting the setting this is a floor for; the key it is stored under on the chart
     * @param label   what the row is called, short enough to sit beside its two steppers
     * @param best    what an untouched chart plans at. Register through {@link Minimum#strongest} or
     *                {@link Minimum#weakest} rather than passing this: which end a setting defaults to
     *                follows from what kind of setting it is, and those two name the kinds
     * @param name    what a tier reads as - the block a player places, not the number stored
     */
    public record Minimum(Settings setting, String label, int min, int max, int best, IntFunction<String> name) {

        /**
         * A setting a game gates progress with - a coil, a capacitor. An untouched chart plans at the
         * best the pack ships, because that is what a player who has got this far can build.
         */
        @Nonnull
        public static Minimum strongest(final Settings setting, final String label, final int min, final int max,
            final IntFunction<String> name) {
            return new Minimum(setting, label, min, max, max, name);
        }

        /**
         * A setting that costs rather than unlocks. Raising this floor makes every recipe more expensive
         * instead of making it buildable, so an untouched chart plans at the cheapest end.
         */
        @Nonnull
        public static Minimum weakest(final Settings setting, final String label, final int min, final int max,
            final IntFunction<String> name) {
            return new Minimum(setting, label, min, max, min, name);
        }

        /** What this chart plans at, falling back to {@link #best} while it has said nothing. */
        public int current(@Nonnull final Graph graph) {
            final int held = graph.getMinimum(setting.key());
            return held == Graph.NO_MINIMUM ? best : held;
        }

        /**
         * Steps the chart, never off either end. The unset marker is deliberately not reachable: it
         * means "whatever the game allows", which is a value already in the range, so offering it as a
         * separate step would be one click that changes nothing visible.
         */
        public void step(@Nonnull final Graph graph, final int by) {
            graph.setMinimum(setting.key(), Math.max(min, Math.min(max, current(graph) + by)));
        }
    }

    private static final List<Minimum> registered = new ArrayList<>();

    public static void register(@Nonnull final Minimum minimum) {
        registered.add(minimum);
    }

    /** In registration order, so the panel lists a mod's settings the way that mod declared them. */
    @Nonnull
    public static List<Minimum> all() {
        return List.copyOf(registered);
    }

    public static void reset() {
        registered.clear();
    }

    /**
     * What the open chart plans at for one setting, or {@code best} when it has said nothing.
     *
     * <p>
     * Read from the chart on screen rather than handed in: a {@link SettingDef} is given the recipe
     * and the node's own settings, never the node or the graph holding it, and only the active chart
     * draws rows. Reaching the open plan reaches Minecraft, through the save directory, so outside a
     * running game there is no chart to read and the answer is the untouched one - which is the state
     * a headless test and the machine probe both want.
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
