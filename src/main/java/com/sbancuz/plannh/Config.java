package com.sbancuz.plannh;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;

import com.sbancuz.plannh.gui.layout.LayoutSettings;

public final class Config {

    /** Dev diagnostic: log a headless repro of every arrow-routing recompute. */
    public static boolean debugRouteDump = false;

    /**
     * Drops the summary's Machine Counts section entirely rather than folding it: a fold is "not on
     * this chart" and lives per slot, this is "never" and lives with the install.
     */
    public static boolean hideMachineCountsSection = false;

    /**
     * Ingredients nobody plumbs, so the wiring diagnostics stay quiet about them. Display names
     * rather than registry ids: this is a nuisance filter the user edits by hand.
     */
    private static final String[] DEFAULT_FREE_INGREDIENTS = { "Water" };

    /** {@link #DEFAULT_FREE_INGREDIENTS} folded to lower case for lookup. */
    private static Set<String> freeIngredients = lowercased(DEFAULT_FREE_INGREDIENTS);

    /** Whether wiring diagnostics should stay quiet about this ingredient. */
    public static boolean isFreeIngredient(final String displayName) {
        return freeIngredients.contains(
            displayName.trim()
                .toLowerCase(Locale.ROOT));
    }

    /** Replaces the free list. Names are matched case- and whitespace-insensitively from here on. */
    public static void setFreeIngredients(final String... names) {
        freeIngredients = lowercased(names);
    }

    public static void resetFreeIngredients() {
        setFreeIngredients(DEFAULT_FREE_INGREDIENTS);
    }

    private static Set<String> lowercased(final String[] names) {
        final Set<String> set = new HashSet<>();
        for (final String name : names) {
            set.add(
                name.trim()
                    .toLowerCase(Locale.ROOT));
        }
        return set;
    }

    /**
     * How long the balancer is allowed to look for a better answer, as a percentage of the tuned
     * default. One number rather than one per budget, because the budgets are not independent -
     * halving the time a solve gets and leaving the tie enumeration at five candidates only means
     * running out of time inside the enumeration.
     *
     * <p>
     * Spent on a background thread, so the ceiling is about patience rather than the GUI: a solve
     * that runs long shows the chart its previous answer for longer, and nothing freezes.
     */
    public static int solverEffortPercent = 100;

    public static final int SOLVER_EFFORT_MIN = 25;
    public static final int SOLVER_EFFORT_MAX = 200;

    /**
     * {@link #solverEffortPercent} as the solver reads it. Clamped rather than trusted: Forge
     * clamps what it parses out of the config file, but the field is public and nothing stops a
     * later caller from assigning to it, and an unclamped percentage multiplies a 20-second budget.
     */
    public static int solverEffort() {
        return Math.clamp(solverEffortPercent, SOLVER_EFFORT_MIN, SOLVER_EFFORT_MAX);
    }

    // --- Auto-layout -----------------------------------------------------------------------
    //
    // Three knobs, and three is a considered number rather than a stopping point. A user has a real
    // reason to change these: node spacing changes how tight a column is, layer spacing changes whether
    // the chart fits on one screen, and thoroughness trades pause time against untangling. An earlier
    // attempt also exposed cycle breaking, node placement and flow direction, and its own post-mortem
    // records that those "move the corpus measurably and a small chart not at all" - which is a report of
    // "the config did nothing" being accurate. Those live as constants in the strategy now, with their
    // measurements beside them, and promoting one is a field plus a getInt.

    /** Clear space between two machines in the same column. */
    public static int layoutNodeSpacing = 20;

    /**
     * Clear space between two columns. This is the arrow router's corridor and the single most
     * consequential number here: the router needs {@code 2 * ROUTE_MARGIN + ROUTE_CELL} units of it to
     * turn in, and {@link #layoutSettings(int, int)} clamps up to that.
     */
    public static int layoutLayerSpacing = 70;

    /**
     * How many crossing-minimisation sweeps the engine attempts, keeping the best. Higher takes
     * longer; the chart keeps the positions it has until the new arrangement is ready.
     */
    public static int layoutThoroughness = 30;

    public static final int LAYOUT_NODE_SPACING_MIN = 10;
    public static final int LAYOUT_NODE_SPACING_MAX = 80;
    public static final int LAYOUT_LAYER_SPACING_MIN = 40;
    public static final int LAYOUT_LAYER_SPACING_MAX = 200;
    public static final int LAYOUT_THOROUGHNESS_MIN = 1;
    public static final int LAYOUT_THOROUGHNESS_MAX = 30;

    /**
     * The settings the engine reads, clamped the same way {@link #solverEffort()} is.
     *
     * @param corridorFloor  the minimum inter-column gap the arrow router needs to turn in. Passed in
     *                       rather than duplicated here, because the router owns those numbers and a
     *                       hand-transcribed copy of them was once wrong by six units and never called
     * @param groupMinWidth  the narrowest a group's content area may be drawn
     * @param groupMinHeight the shortest, likewise
     */
    public static LayoutSettings layoutSettings(final int corridorFloor, final int groupMinWidth,
        final int groupMinHeight) {

        final int layerSpacing = Math.clamp(layoutLayerSpacing, LAYOUT_LAYER_SPACING_MIN, LAYOUT_LAYER_SPACING_MAX);
        if (layerSpacing < corridorFloor) {
            PlanNH.LOG.info(
                "Auto-layout layer spacing {} is below the {} the arrow router needs to turn in; using {}",
                layerSpacing,
                corridorFloor,
                corridorFloor);
        }
        return new LayoutSettings(
            Math.clamp(layoutNodeSpacing, LAYOUT_NODE_SPACING_MIN, LAYOUT_NODE_SPACING_MAX),
            Math.max(layerSpacing, corridorFloor),
            Math.clamp(layoutThoroughness, LAYOUT_THOROUGHNESS_MIN, LAYOUT_THOROUGHNESS_MAX),
            groupMinWidth,
            groupMinHeight);
    }

    public static void synchronizeConfiguration(final File configFile) {
        final Configuration configuration = new Configuration(configFile);

        debugRouteDump = configuration.getBoolean(
            "debugRouteDump",
            "debug",
            false,
            "Log a replayable dump of the arrow-routing input on every route recompute");

        hideMachineCountsSection = configuration.getBoolean(
            "hideMachineCountsSection",
            "gui",
            false,
            "Leave the Machine Counts section (per-machine operation counts, and the ops/cycle"
                + " totals) out of the summary panel altogether, instead of folding it away");

        setFreeIngredients(
            configuration
                .get(
                    "solver",
                    "freeIngredients",
                    DEFAULT_FREE_INGREDIENTS,
                    "Ingredients the solver never suggests wiring up. Display names, case"
                        + " insensitive. Anything effectively free in the pack belongs here:"
                        + " otherwise every chart that takes water from outside reports a missing"
                        + " edge to whatever else happens to produce it.")
                .getStringList());

        solverEffortPercent = configuration.getInt(
            "solverEffortPercent",
            "solver",
            100,
            SOLVER_EFFORT_MIN,
            SOLVER_EFFORT_MAX,
            "How long AUTO balancing may spend looking for a better answer, as a percentage of the"
                + " default. Lower gives up sooner on big charts; higher balances them better, and"
                + " the chart keeps showing its last answer for longer while the new one is worked out.");

        layoutNodeSpacing = configuration.getInt(
            "layoutNodeSpacing",
            "layout",
            layoutNodeSpacing,
            LAYOUT_NODE_SPACING_MIN,
            LAYOUT_NODE_SPACING_MAX,
            "Clear space between two machines in the same column when auto-layout arranges a chart."
                + " Lower packs a column tighter, which usually means a wider chart as arrows have"
                + " further to travel.");

        layoutLayerSpacing = configuration.getInt(
            "layoutLayerSpacing",
            "layout",
            layoutLayerSpacing,
            LAYOUT_LAYER_SPACING_MIN,
            LAYOUT_LAYER_SPACING_MAX,
            "Clear space between two columns when auto-layout arranges a chart. This gap is also the"
                + " corridor the arrows route down, so it is raised automatically if it would leave the"
                + " router no room to turn.");

        layoutThoroughness = configuration.getInt(
            "layoutThoroughness",
            "layout",
            layoutThoroughness,
            LAYOUT_THOROUGHNESS_MIN,
            LAYOUT_THOROUGHNESS_MAX,
            "How hard auto-layout works to untangle arrow crossings, from 1 to 30. Higher takes longer,"
                + " and a chart keeps its current positions until the new arrangement is ready.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
