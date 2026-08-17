package com.sbancuz.plannh.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.data.flowchart.Graph;

/**
 * The knobs a chart can set a floor for, contributed by whichever mods are installed.
 *
 * <p>
 * A floor belongs to the chart rather than to each node - a chart describes a factory at one point in
 * a world's progression, so asking every node which coil it may use is asking the same question fifty
 * times. Which knobs have a floor at all is a different question, and only the mod that owns the
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
     * @param setting the knob this is a floor for; the key it is stored under on the chart
     * @param label   what the row is called, short enough to sit beside its two steppers
     * @param best    what an untouched chart plans at. The strongest structure for the knobs a game
     *                gates progress with, and the weakest for a cost knob like voltage, where a floor
     *                raises what a recipe costs rather than making it buildable
     * @param name    what a tier reads as - the block a player places, not the number stored
     */
    public record Minimum(Settings setting, String label, int min, int max, int best, IntFunction<String> name) {

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

    /** In registration order, so the panel lists a mod's knobs the way that mod declared them. */
    @Nonnull
    public static List<Minimum> all() {
        return List.copyOf(registered);
    }

    public static void reset() {
        registered.clear();
    }
}
