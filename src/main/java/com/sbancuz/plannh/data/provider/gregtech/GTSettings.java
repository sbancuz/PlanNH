package com.sbancuz.plannh.data.provider.gregtech;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.steps.GTOverclockStep;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.provider.GTProvider;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.util.GTUtility;

/**
 * Settings that only exist for GregTech nodes, kept out of {@link com.sbancuz.plannh.data.Settings}
 * because their option lists come from GT enums and {@code data} must stay loadable without it.
 *
 * <p>
 * These describe the structure a player built - which coil, which solenoid - rather than raw
 * overclock arithmetic. Each row is shown only when the selected machine's preset says it reads that
 * knob, so a node asks for the two or three numbers that machine actually uses instead of the
 * fifteen the old profiles offered.
 */
public final class GTSettings {

    private GTSettings() {}

    public static final String MACHINE = "gt_machine";
    public static final String ADVANCED = "gt_advanced";

    // Read off the shared vocabulary rather than repeated as literals, so the key a preset names and
    // the key a node stores cannot drift apart. Sourcing them from a method call also keeps them out
    // of the constant pool, which is what makes a single edit here reach every call site.
    public static final String COIL = Settings.GT_COIL.key();
    public static final String SOLENOID = Settings.GT_SOLENOID.key();
    public static final String ITEM_PIPE = Settings.GT_ITEM_PIPE.key();
    public static final String PIPE_CASING = Settings.GT_PIPE_CASING.key();
    public static final String SAWBLADE = Settings.GT_SAWBLADE.key();
    public static final String ELECTRODE = Settings.GT_ELECTRODE.key();
    public static final String STRUCTURE_TIER = Settings.GT_STRUCTURE_TIER.key();
    public static final String WIDTH = Settings.GT_WIDTH.key();
    public static final String MODE = Settings.GT_MODE.key();

    /** Sixteen 4A hatches is past anything GregTech builds, and the row is a plan rather than a limit. */
    private static final int MAX_AMPERAGE = 64;

    /**
     * Coil names in GT's tier order, so index 0 is Cupronickel. {@code HeatingCoilLevel} counts None
     * and ULV below that, which is why its {@code getTier()} subtracts two.
     */
    public static final List<String> COIL_NAMES = coilNames();

    @Nonnull
    private static List<String> coilNames() {
        final List<String> names = new ArrayList<>();
        for (int tier = 0; tier <= GTStructureTiers.MAX_COIL_TIER; tier++) {
            names.add(
                HeatingCoilLevel.getFromTier((byte) tier)
                    .name());
        }
        return List.copyOf(names);
    }

    /**
     * The machine picker. Options are the GT machines that can run this node's recipe, best-first,
     * so an unset value renders and behaves as the obvious choice without being serialized.
     */
    public static final SettingDef<String> MACHINE_DEF = SettingDef.dynamicEnumDef(MACHINE, "", ctx -> {
        final List<String> ids = new ArrayList<>();
        for (final GTMachineIndex.MachineEntry entry : GTMachineIndex.candidates(ctx)) {
            ids.add(entry.id());
        }
        return ids;
    }, GTSettings::machineDisplayName, null);

    /**
     * Voltage offered from the lowest tier that can actually run this recipe upward. A machine below
     * the recipe's own EU/t cannot run it at all, so those tiers are not choices, and there is no
     * "off": a GT node always draws power. Unset resolves to the chart's own tier, raised to that
     * minimum, so a fresh node already reads as something buildable rather than as nothing.
     */
    public static final SettingDef<String> VOLTAGE_DEF = SettingDef
        .dynamicEnumDef(Settings.VOLTAGE.key(), "", GTSettings::voltageOptions, name -> name, (v, c) -> v)
        .withDefault(ctx -> GTValues.VN[defaultVoltageTier(GTOverclockStep.recipeEUt(ctx))]);

    @Nonnull
    private static List<String> voltageOptions(final RecipeContext ctx) {
        return voltageOptions(GTOverclockStep.recipeEUt(ctx));
    }

    public static int minimumVoltageTier(final RecipeContext ctx) {
        return minimumVoltageTier(GTOverclockStep.recipeEUt(ctx));
    }

    /** The lowest tier whose voltage covers the recipe's EU/t. */
    public static int minimumVoltageTier(final long recipeEUt) {
        if (recipeEUt <= 0) return 0;
        return Math.min(GTUtility.getTier(recipeEUt), GTValues.VN.length - 2);
    }

    public static int voltageTier(final RecipeContext ctx, final Map<String, Object> settings) {
        return voltageTier(GTOverclockStep.recipeEUt(ctx), settings);
    }

    /** The tier a node runs at: what it stored, or the recipe's minimum when it stored nothing. */
    public static int voltageTier(final long recipeEUt, final Map<String, Object> settings) {
        final String stored = MachineProfile.getString(settings, Settings.VOLTAGE.key(), "");
        final int minimum = minimumVoltageTier(recipeEUt);
        for (int tier = 0; tier < GTValues.VN.length; tier++) {
            if (GTValues.VN[tier].equals(stored)) return Math.max(tier, minimum);
        }
        return defaultVoltageTier(recipeEUt);
    }

    /**
     * The tier a node opens at: the chart's own minimum, raised to the lowest tier that can run this
     * recipe at all. A node the user has not set is planning at whatever this chart plans at, and a
     * recipe too expensive for that still gets a hatch that works.
     */
    public static int defaultVoltageTier(final long recipeEUt) {
        return Math.max(minimumVoltageTier(recipeEUt), chartMinimum(Graph::getMinVoltageTier, 0));
    }

    /**
     * What a chart says it can build, or the best the game offers when it has not said. Read from the
     * chart on screen rather than handed in: a {@link SettingDef} is given the recipe and the node's
     * own settings, never the node or the graph holding it, and only the active chart draws rows.
     */
    private static int chartMinimum(final ToIntFunction<Graph> minimum, final int best) {
        final Integer floor = insideAGame(() -> minimum.applyAsInt(Plan.getActiveGraph()), null);
        return floor == null || floor == Graph.NO_MINIMUM ? best : floor;
    }

    /**
     * Something that only answers inside a running game, and its answer when there is none. Resolving
     * a structure reaches the open plan and the recipe's own properties, and both of those reach
     * Minecraft: the plan through the save directory, the properties through the provider that
     * declares them. A test and the probe's warmup sweep resolve structures with neither loaded, and
     * that is not a failure - it means nothing has been chosen yet.
     */
    @Nullable
    private static <T> T insideAGame(final Supplier<T> value, @Nullable final T otherwise) {
        try {
            return value.get();
        } catch (final RuntimeException | LinkageError outsideAGame) {
            return otherwise;
        }
    }

    /** The tiers offered for a recipe of this cost, lowest usable first. */
    @Nonnull
    public static List<String> voltageOptions(final long recipeEUt) {
        final List<String> names = new ArrayList<>();
        for (int tier = minimumVoltageTier(recipeEUt); tier < GTValues.VN.length - 1; tier++) {
            names.add(GTValues.VN[tier]);
        }
        return names;
    }

    /**
     * Hands the node back to the raw overclock numbers. Also the provenance marker: while it is on,
     * the settings map is what the user meant and no preset may override it.
     */
    public static final SettingDef<Boolean> ADVANCED_DEF = SettingDef
        .boolDef(ADVANCED, false, (v, c) -> v ? "A" : null);

    /**
     * Parallels sit at whatever the structure allows, because running a multiblock below its maximum
     * is almost never what a player wants. Stored 0 means exactly that, so the number follows the
     * machine and its coils instead of freezing at whatever was current when the node was made.
     */
    public static final SettingDef<Integer> PARALLELS_DEF = SettingDef
        .autoIntDefCapped(Settings.PARALLELS.key(), 1, 4096, 1, GTSettings::machineMaxParallel, (v, c) -> "∥" + v);

    /** The selected machine's own parallel count for the structure the node describes. */
    public static int machineMaxParallel(final RecipeContext ctx, final Map<String, Object> settings) {
        return Math.max(
            1,
            fromPreset(
                ctx,
                settings,
                1,
                (preset, state) -> preset.maxParallel()
                    .applyAsInt(state)));
    }

    /**
     * Reads one number off the machine the node is using, for the structure it describes. Every
     * advanced row resolves this way, so an untouched row reads what the machine actually does
     * instead of a global default that happens to be wrong for it.
     */
    private static int fromPreset(final RecipeContext ctx, final Map<String, Object> settings, final int fallback,
        final ToIntBiFunction<GTMachinePreset, StructureState> reader) {
        final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, settings);
        if (entry == null || entry.preset() == null) return fallback;
        return reader
            .applyAsInt(entry.preset(), resolve(ctx, settings, voltageTier(ctx, settings), mode(ctx, entry, settings)));
    }

    /** Percentages the rows show; the maths uses the preset's exact doubles, never these. */
    public static final SettingDef<Integer> SPEED_DEF = SettingDef.autoIntDef(
        Settings.SPEED.key(),
        10,
        10000,
        100,
        (ctx, s) -> fromPreset(
            ctx,
            s,
            100,
            (p, st) -> (int) Math.round(
                100.0 / p.durationModifier()
                    .applyAsDouble(st))),
        (v, c) -> "⏱" + v + "%");

    public static final SettingDef<Integer> EUT_DISCOUNT_DEF = SettingDef.autoIntDef(
        Settings.EUT_DISCOUNT.key(),
        0,
        100,
        100,
        (ctx, s) -> fromPreset(
            ctx,
            s,
            100,
            (p, st) -> (int) Math.round(
                100.0 * p.euModifier()
                    .applyAsDouble(st))),
        (v, c) -> "D" + v + "%");

    public static final SettingDef<Integer> EUT_PER_OC_DEF = SettingDef.autoIntDef(
        Settings.EUT_INCREASE_PER_OC.key(),
        100,
        1000,
        400,
        (ctx, s) -> fromPreset(
            ctx,
            s,
            400,
            (p, st) -> (int) Math.round(
                100.0 * p.eutIncreasePerOC()
                    .applyAsDouble(st))),
        (v, c) -> "EU×" + (v / 100));

    public static final SettingDef<Integer> DURATION_PER_OC_DEF = SettingDef.autoIntDef(
        Settings.DURATION_DECREASE_PER_OC.key(),
        100,
        1000,
        200,
        (ctx, s) -> fromPreset(
            ctx,
            s,
            200,
            (p, st) -> (int) Math.round(
                100.0 * p.durationDecreasePerOC()
                    .applyAsDouble(st))),
        (v, c) -> "Spd×" + (v / 100));

    public static final SettingDef<Integer> MACHINE_HEAT_DEF = SettingDef.autoIntDef(
        Settings.MACHINE_HEAT.key(),
        0,
        100000,
        0,
        (ctx, s) -> fromPreset(
            ctx,
            s,
            0,
            (p, st) -> p.machineHeat()
                .applyAsInt(st)),
        (v, c) -> "M" + v);

    /**
     * The heat a recipe demands, which GregTech keeps in the recipe's special value. That field holds
     * whatever each machine wants it to - the Chemical Plant keeps its required casing tier there - so
     * it is only heat for a machine that overclocks on heat. Every other machine reports zero, because
     * a row that shows a number nothing reads is worse than no row.
     */
    public static final SettingDef<Integer> RECIPE_HEAT_DEF = SettingDef
        .autoIntDef(Settings.RECIPE_HEAT.key(), 0, 100000, 0, (ctx, s) -> {
            final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, s);
            final GTMachinePreset preset = entry == null ? null : entry.preset();
            if (preset == null || !preset.usesHeat()) return 0;
            return GTPresetApplier.recipeHeat(ctx, preset);
        }, (v, c) -> "R" + v);

    /** GT's own heat discount base, 0.95 per 900K of headroom. */
    public static final SettingDef<Integer> HEAT_DISCOUNT_MULT_DEF = SettingDef
        .autoIntDef(Settings.HEAT_DISCOUNT_MULT.key(), 0, 200, 95, (ctx, s) -> 95, null);

    /**
     * A machine that skips no tiers reports 0, which is a real answer. -1 on the preset means the
     * machine never asked, leaving GT's own default of one.
     */
    public static final SettingDef<Integer> MAX_TIER_SKIPS_DEF = SettingDef.autoIntDef(
        Settings.MAX_TIER_SKIPS.key(),
        0,
        10,
        1,
        (ctx, s) -> fromPreset(
            ctx,
            s,
            1,
            (p, st) -> p.maxTierSkips() == GTMachinePreset.TIER_SKIPS_UNSET ? 1 : p.maxTierSkips()),
        (v, c) -> "Sk" + v);

    public static final SettingDef<Boolean> PERFECT_OC_DEF = SettingDef.autoBoolDef(
        Settings.PERFECT_OC.key(),
        (ctx, s) -> fromPreset(
            ctx,
            s,
            0,
            (p, st) -> p.durationDecreasePerOC()
                .applyAsDouble(st) >= 4.0 ? 1 : 0),
        (v, c) -> v ? "P" : null);

    public static final SettingDef<Boolean> HEAT_OC_DEF = SettingDef.autoBoolDef(
        Settings.HEAT_OC.key(),
        (ctx, s) -> fromPreset(ctx, s, 0, (p, st) -> p.heatOC() ? 1 : 0),
        (v, c) -> v ? "H" : null);

    public static final SettingDef<Boolean> HEAT_DISCOUNT_DEF = SettingDef.autoBoolDef(
        Settings.HEAT_DISCOUNT.key(),
        (ctx, s) -> fromPreset(ctx, s, 0, (p, st) -> p.heatDiscount() ? 1 : 0),
        (v, c) -> v ? "D" : null);

    public static final SettingDef<Boolean> UNLIMITED_SKIPS_DEF = SettingDef.autoBoolDef(
        Settings.UNLIMITED_SKIPS.key(),
        (ctx, s) -> fromPreset(ctx, s, 0, (p, st) -> p.unlimitedTierSkips() ? 1 : 0),
        (v, c) -> v ? "∞T" : null);

    /**
     * Amperage comes from the machine block itself rather than from a preset formula, and is a floor
     * rather than a ceiling: a multiblock draws whatever its energy hatches supply, so the row must
     * step past what the machine reports on its own.
     */
    public static final SettingDef<Integer> AMP_DEF = SettingDef
        .autoIntDef(Settings.AMP.key(), 1, MAX_AMPERAGE, 1, (ctx, s) -> {
            final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, s);
            return entry == null ? 1 : Math.max(1, entry.amperage());
        }, (v, c) -> "A" + v);

    /**
     * Stores GregTech's tier name and shows GregTech's material name, because the tier name is what a
     * save can keep - it is locale-independent and stable - while "Cupronickel" is what a player built.
     *
     * <p>
     * An untouched row opens on the chart's own coil rather than on the best one, because a coil sets
     * the heat every overclock is counted from and a chart planned at Cupronickel that quotes Eternal
     * numbers is wrong everywhere at once. The whole list stays offered, so one node can still model a
     * hotter build than the rest of the chart.
     */
    public static final SettingDef<String> COIL_DEF = SettingDef
        .dynamicEnumDef(COIL, "", ctx -> COIL_NAMES, GTSettings::coilDisplayName, (v, c) -> null)
        .withDefault(ctx -> COIL_NAMES.get(defaultCoilTier(ctx)));

    /**
     * The coil a node opens on: the chart's own minimum, raised to whatever the recipe needs to reach
     * its heat. GregTech keeps that heat in the recipe's special value, which a machine that ignores
     * heat uses for something else - but a casing tier or a mode number sits far below the weakest
     * coil's 1801K, so reading it here raises nothing.
     */
    public static int defaultCoilTier(final RecipeContext ctx) {
        return Math.max(chartMinimum(Graph::getMinCoilTier, GTStructureTiers.MAX_COIL_TIER), coilTierForRecipe(ctx));
    }

    private static int coilTierForRecipe(final RecipeContext ctx) {
        final Integer heat = insideAGame(() -> ctx.getOrDefault(GTProvider.SPECIAL_VALUE, null), null);
        return heat == null ? 0 : coilTierForHeat(heat);
    }

    /**
     * The weakest coil that reaches a heat, or the hottest coil when none does. Public because it is
     * the rule the recipe-driven part of a coil default is, and it is worth pinning on its own: one
     * tier too low and a node opens on a structure that cannot run its recipe.
     */
    public static int coilTierForHeat(final int heat) {
        if (heat <= 0) return 0;
        for (int tier = 0; tier < GTStructureTiers.MAX_COIL_TIER; tier++) {
            if (GTStructureTiers.coilHeat(tier) >= heat) return tier;
        }
        return GTStructureTiers.MAX_COIL_TIER;
    }

    /** The pipe casing a node opens on. GregTech attaches no casing requirement to a recipe. */
    public static int defaultPipeCasingTier() {
        return chartMinimum(Graph::getMinPipeCasingTier, GTStructureTiers.MAX_PIPE_CASING_TIER);
    }

    /** GregTech's own translated name for a coil tier, so the row reads as the block a player places. */
    @Nonnull
    private static String coilDisplayName(final String tierName) {
        for (final HeatingCoilLevel level : HeatingCoilLevel.values()) {
            if (level.name()
                .equals(tierName)) return level.getName();
        }
        return tierName;
    }

    public static final SettingDef<Integer> SOLENOID_DEF = SettingDef.intDef(
        SOLENOID,
        GTStructureTiers.MAX_SOLENOID_TIER,
        GTStructureTiers.MIN_SOLENOID_TIER,
        GTStructureTiers.MAX_SOLENOID_TIER);
    public static final SettingDef<Integer> ITEM_PIPE_DEF = SettingDef
        .intDef(ITEM_PIPE, GTStructureTiers.MAX_ITEM_PIPE_TIER, 1, GTStructureTiers.MAX_ITEM_PIPE_TIER);
    /**
     * Stores GregTech's tier number, which is what the machines read, and shows the casing it means.
     * An untouched row follows the chart, so it is an automatic row rather than one with a fixed
     * default.
     */
    public static final SettingDef<Integer> PIPE_CASING_DEF = SettingDef
        .autoIntDef(
            PIPE_CASING,
            1,
            GTStructureTiers.MAX_PIPE_CASING_TIER,
            null,
            (ctx, s) -> defaultPipeCasingTier(),
            null)
        .withDisplay(tier -> GTStructureTiers.pipeCasingName(Integer.parseInt(tier)));
    public static final SettingDef<Integer> SAWBLADE_DEF = SettingDef
        .intDef(SAWBLADE, GTStructureTiers.MAX_SAWBLADE_TIER, 0, GTStructureTiers.MAX_SAWBLADE_TIER);
    public static final SettingDef<Integer> ELECTRODE_DEF = SettingDef
        .intDef(ELECTRODE, 0, 0, GTStructureTiers.MAX_ELECTRODE_TIER);
    public static final SettingDef<Integer> STRUCTURE_TIER_DEF = SettingDef.intDef(STRUCTURE_TIER, 2, 0, 2);
    public static final SettingDef<Integer> WIDTH_DEF = SettingDef
        .intDef(WIDTH, GTStructureTiers.MAX_WIDTH, 0, GTStructureTiers.MAX_WIDTH);
    /**
     * How many modes a machine has is the machine's business, not a constant: GregTech ships three-mode
     * multiblocks, and a fixed ceiling of one would leave the third unreachable.
     */
    public static final SettingDef<Integer> MODE_DEF = SettingDef.intDef(MODE, 0, 0, GTSettings::modeCeiling);

    private static int modeCeiling(final RecipeContext ctx, final Map<String, Object> settings) {
        final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, settings);
        return entry == null ? 1
            : entry.modes()
                .count() - 1;
    }

    /** What a structure knob can be set to, both ends included. */
    public record TierRange(int min, int max) {}

    /**
     * The row a structure knob is edited through. The single place that says which def belongs to
     * which setting, so a profile listing the rows and a scan sweeping their ranges cannot disagree
     * about what a knob is.
     */
    @Nonnull
    public static SettingDef<?> knobDef(final Settings knob) {
        return switch (knob) {
            case GT_COIL -> COIL_DEF;
            case GT_SOLENOID -> SOLENOID_DEF;
            case GT_ITEM_PIPE -> ITEM_PIPE_DEF;
            case GT_PIPE_CASING -> PIPE_CASING_DEF;
            case GT_SAWBLADE -> SAWBLADE_DEF;
            case GT_ELECTRODE -> ELECTRODE_DEF;
            case GT_STRUCTURE_TIER -> STRUCTURE_TIER_DEF;
            case GT_WIDTH -> WIDTH_DEF;
            case GT_MODE -> MODE_DEF;
            default -> throw new IllegalArgumentException(knob + " is not a structure knob");
        };
    }

    /**
     * The range a knob offers, read off the row that offers it. Anything that varies a knob - the
     * probe's sensitivity scan - then covers exactly what the player can reach, and one edit to a row
     * moves both.
     */
    @Nonnull
    public static TierRange knobRange(final Settings knob) {
        // The coil row stores a name rather than a number, so its range is the name list.
        if (knob == Settings.GT_COIL) return new TierRange(0, COIL_NAMES.size() - 1);
        // A sweep over modes takes its count from the machine, not from a range; the mode row's own
        // ceiling is a function of the selected machine and so cannot answer without one.
        if (knob == Settings.GT_MODE) return new TierRange(0, 1);
        final SettingDef<?> def = knobDef(knob);
        return new TierRange(def.minInt, def.maxInt);
    }

    /**
     * Structure knobs open on what the chart says it can build, and on the best the game offers where
     * the chart has said nothing. The row is right there to move one node off that.
     */
    @Nonnull
    public static StructureState resolve(final RecipeContext ctx, final Map<String, Object> settings,
        final int voltageTier) {
        return resolve(ctx, settings, voltageTier, MachineProfile.getInt(settings, MODE, 0));
    }

    /**
     * As above, with the mode supplied by a caller that already knows the machine. Kept separate so
     * that resolving a structure never reaches the machine index, which a chart does per frame.
     */
    @Nonnull
    public static StructureState resolve(final RecipeContext ctx, final Map<String, Object> settings,
        final int voltageTier, final int mode) {
        return new StructureState(
            voltageTier,
            COIL_NAMES.indexOf(MachineProfile.getString(settings, COIL, COIL_NAMES.get(defaultCoilTier(ctx)))),
            MachineProfile.getInt(settings, SOLENOID, GTStructureTiers.MAX_SOLENOID_TIER),
            MachineProfile.getInt(settings, ITEM_PIPE, GTStructureTiers.MAX_ITEM_PIPE_TIER),
            MachineProfile.getInt(settings, PIPE_CASING, defaultPipeCasingTier()),
            MachineProfile.getInt(settings, SAWBLADE, GTStructureTiers.MAX_SAWBLADE_TIER),
            MachineProfile.getInt(settings, ELECTRODE, 0),
            MachineProfile.getInt(settings, STRUCTURE_TIER, 2),
            MachineProfile.getInt(settings, WIDTH, GTStructureTiers.MAX_WIDTH),
            mode);
    }

    /**
     * The machine mode this node runs in. A machine that is two machines behind one controller picks
     * between them by recipemap, and the node's recipe already came from one of them, so the answer is
     * read rather than asked for. Everything else falls back to the row.
     */
    public static int mode(final RecipeContext ctx, @Nullable final GTMachineIndex.MachineEntry entry,
        final Map<String, Object> settings) {
        final int implied = entry == null ? -1 : entry.modeFor(ctx.getOrDefault(GTProvider.RECIPE_MAP, null));
        return implied >= 0 ? implied : MachineProfile.getInt(settings, MODE, 0);
    }

    public static boolean isAdvanced(final Map<String, Object> settings) {
        return MachineProfile.getBool(settings, ADVANCED, false);
    }

    /** Shows a knob only when the machine the node selected actually reads it. */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> usesKnob(final Settings knob) {
        return (ctx, settings) -> {
            if (isAdvanced(settings)) return false;
            final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, settings);
            if (entry == null || entry.preset() == null) return false;
            // No row for a question the recipe has already answered.
            if (knob == Settings.GT_MODE && entry.modeFor(ctx.getOrDefault(GTProvider.RECIPE_MAP, null)) >= 0)
                return false;
            return entry.preset()
                .knobs()
                .contains(knob);
        };
    }

    /** The raw overclock rows, shown only once the user has asked for them. */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> advancedOnly() {
        return (ctx, settings) -> isAdvanced(settings);
    }

    /**
     * The machine is chosen from the node's title bar, not from a settings row - it names what the
     * node is, rather than tuning it. The def still belongs to the profile so the choice serializes;
     * it just never draws.
     */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> neverAsARow() {
        return (ctx, settings) -> false;
    }

    /**
     * A singleblock's tier is the block you placed, so only a multiblock's energy hatch is a choice.
     * Also shown when nothing resolved, so a node PlanNH cannot identify keeps a usable control.
     */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> voltageEditable() {
        return (ctx, settings) -> multiblockOrUnknown(ctx, settings);
    }

    /**
     * Amperage is the energy hatches a multiblock was built with, so it is a choice wherever the
     * machine is one. A singleblock draws the amperage its block draws and has nothing to say.
     */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> ampEditable() {
        return (ctx, settings) -> multiblockOrUnknown(ctx, settings);
    }

    /**
     * The preset already gives the machine's maximum, but planning for fewer than the structure
     * allows is normal, so the cap stays editable wherever it can exceed one. A multiblock that runs
     * one recipe at a time - the Large Chemical Reactor, the IsaMill - reports a maximum of one, and
     * a row that can only be moved below what the machine does is not a plan anybody draws.
     */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> parallelsEditable() {
        return (ctx, settings) -> multiblockOrUnknown(ctx, settings)
            && (isAdvanced(settings) || machineMaxParallel(ctx, settings) > 1);
    }

    /**
     * Whether the node stands for a multiblock. The machine picker already answers this, so the row is
     * never a question - but it stays a setting, because a node whose machine PlanNH cannot identify
     * still needs a way to say which form factor it is, and because a preset may want to state it.
     * Unknown counts as a multiblock: the rows it gates are the ones a multiblock has, and offering
     * them on a machine that turns out to be a singleblock is recoverable where withholding them is
     * not.
     */
    public static final SettingDef<Boolean> MULTIBLOCK_DEF = SettingDef
        .autoBoolDef(Settings.GT_MULTIBLOCK.key(), (ctx, s) -> {
            final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, s);
            return entry == null || entry.kind() == GTMachineIndex.Kind.MULTIBLOCK ? 1 : 0;
        }, (v, c) -> v ? "M" : null);

    private static boolean multiblockOrUnknown(final RecipeContext ctx, final Map<String, Object> settings) {
        return isAdvanced(settings) || MULTIBLOCK_DEF.effectiveBool(ctx, settings);
    }

    /**
     * Settings the machine now derives. A chart saved before the picker existed has these tuned by
     * hand, and honouring the preset instead would silently change its numbers, so such a chart
     * opens in advanced mode. Voltage and machine count are deliberately absent: they stay
     * user-owned in both modes, so a chart whose only change was "IV, x4" gets the compact UI.
     */
    private static final List<String> DERIVED_KEYS = List.of(
        Settings.AMP.key(),
        Settings.SPEED.key(),
        Settings.PARALLELS.key(),
        Settings.PERFECT_OC.key(),
        Settings.LASER_OC.key(),
        Settings.NO_OVERCLOCK.key(),
        Settings.UNLIMITED_SKIPS.key(),
        Settings.EUT_DISCOUNT.key(),
        Settings.EUT_INCREASE_PER_OC.key(),
        Settings.DURATION_DECREASE_PER_OC.key(),
        Settings.MAX_OVERCLOCKS.key(),
        Settings.MAX_REGULAR_OC.key(),
        Settings.MAX_TIER_SKIPS.key(),
        Settings.MACHINE_HEAT.key(),
        Settings.RECIPE_HEAT.key(),
        Settings.HEAT_OC.key(),
        Settings.HEAT_DISCOUNT.key(),
        Settings.HEAT_DISCOUNT_MULT.key());

    public static void migrateLegacyNode(final Map<String, Object> settings) {
        // The mode is derived from the recipe now. A stored one can contradict it - a chart saved with
        // tower mode on a distillery recipe models a machine that cannot run it - so it is dropped
        // rather than honoured. Machines that still ask for a mode re-store it on the next edit.
        settings.remove(MODE);

        if (settings.containsKey(ADVANCED) || settings.containsKey(MACHINE)) return;
        for (final String key : DERIVED_KEYS) {
            if (settings.containsKey(key)) {
                settings.put(ADVANCED, true);
                return;
            }
        }
    }

    /**
     * GT's own names do not always say which form factor a machine is - "Chemical Reactor" against
     * "Large Chemical Reactor" reads as a size, not as singleblock against multiblock - and the two
     * overclock completely differently. Saying so avoids reading the wrong numbers as a bug.
     */
    @Nonnull
    private static String machineDisplayName(final String id) {
        final GTMachineIndex.MachineEntry entry = GTMachineIndex.byId(id);
        if (entry == null) return id;
        return entry.kind() == GTMachineIndex.Kind.SINGLEBLOCK ? entry.displayName() + " (single)"
            : entry.displayName();
    }
}
