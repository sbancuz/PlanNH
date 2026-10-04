package com.sbancuz.plannh.data.provider.gregtech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.ChartMinimums;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.machine.MachineVariant;
import com.sbancuz.plannh.data.machine.MachineVariants;
import com.sbancuz.plannh.data.provider.GTProvider;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.logic.ModifierKind;
import gregtech.api.logic.ModifierRange;
import gregtech.api.logic.ProcessingInputs;
import gregtech.api.logic.ProcessingSpec;
import gregtech.api.logic.ResolvedRecipe;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.OverclockCalculator;

/**
 * Settings for GregTech nodes only, kept out of {@link com.sbancuz.plannh.data.Settings} because their
 * option lists come from GT enums and {@code data} must load without GT.
 *
 * <p>
 * Rows are the structure a player built (which coil, which solenoid), not raw overclock arithmetic.
 * Each row appears only when the selected machine's spec reads that setting, so a node has rows for
 * the two or three numbers that machine uses.
 */
public final class GTSettings {

    private GTSettings() {}

    /** Shared: any mod's machines are picked through the same row, so a node has one machine key. */
    public static final String MACHINE = Settings.MACHINE.key();
    /** GregTech-only: it enables the raw overclock rows, which no other provider has. */
    public static final String ADVANCED = "gt_advanced";

    // Read from Settings, not repeated as literals, so the key a spec reads and the key a node stores
    // cannot drift apart. As non-constants they are not inlined into call sites, so an edit here
    // reaches every caller.
    public static final String MODE = Settings.GT_MODE.key();
    /** Coil and pipe casing row keys, from {@link #structureKey}. */
    public static final String COIL = structureKey(ModifierKind.COIL);
    public static final String PIPE_CASING = structureKey(ModifierKind.PIPE_CASING);

    /** Sixteen 4A hatches exceeds any real GregTech build. The row is a plan, not a limit. */
    private static final int MAX_AMPERAGE = 64;

    /** Machine picker, built by {@link MachineVariants} from every provider with machines for the node's recipe. */
    public static final SettingDef<String> MACHINE_DEF = MachineVariants.pickerDef();

    /**
     * Voltage options from the lowest tier that can run this recipe upward. Tiers below the recipe's
     * EU/t cannot run it, so they're not options. There is no "off" because a GT node always draws
     * power. Unset resolves to the chart's tier, raised to that minimum, so a new node starts at a
     * buildable tier.
     */
    public static final SettingDef<String> VOLTAGE_DEF = SettingDef
        .dynamicEnumDef(Settings.VOLTAGE.key(), "", GTSettings::voltageOptions, name -> name, (v, c) -> v)
        .withDefault(ctx -> GTValues.VN[defaultVoltageTier(recipeEUt(ctx))]);

    @Nonnull
    private static List<String> voltageOptions(final RecipeContext ctx) {
        return voltageOptions(recipeEUt(ctx));
    }

    /** Recipe's EU/t, which bounds the voltage tiers the settings rows list. */
    public static long recipeEUt(final RecipeContext ctx) {
        return recipeEUt(ctx, ctx.getOrDefault(RecipePropertyAPI.DURATION_TICKS, 0));
    }

    /** As {@link #recipeEUt(RecipeContext)}, falling back on the chart's current EU/t when the recipe has none. */
    static long recipeEUt(final RecipeContext ctx, final EffectResult current) {
        final long fromRecipe = recipeEUt(ctx, current.durationTicks());
        return fromRecipe > 0 ? fromRecipe : current.energyPerT();
    }

    private static long recipeEUt(final RecipeContext ctx, final int duration) {
        final Long euPerTick = ctx.getOrDefault(GTProvider.EU_PER_TICK, null);
        if (euPerTick != null && euPerTick > 0) return euPerTick;
        final Long totalEu = ctx.getOrDefault(GTProvider.TOTAL_EU, null);
        if (totalEu != null && totalEu > 0 && duration > 0) return totalEu / duration;
        return 0;
    }

    public static int minimumVoltageTier(final RecipeContext ctx) {
        return minimumVoltageTier(recipeEUt(ctx));
    }

    /** The lowest tier whose voltage covers the recipe's EU/t. */
    public static int minimumVoltageTier(final long recipeEUt) {
        if (recipeEUt <= 0) return 0;
        return Math.min(GTUtility.getTier(recipeEUt), GTValues.VN.length - 2);
    }

    public static int voltageTier(final RecipeContext ctx, final Map<String, Object> settings) {
        return voltageTier(recipeEUt(ctx), settings);
    }

    /** Tier a node runs at: the stored tier raised to the recipe's minimum, else {@link #defaultVoltageTier}. */
    public static int voltageTier(final long recipeEUt, final Map<String, Object> settings) {
        final String stored = MachineProfile.getString(settings, Settings.VOLTAGE.key(), "");
        final int minimum = minimumVoltageTier(recipeEUt);
        for (int tier = 0; tier < GTValues.VN.length; tier++) {
            if (GTValues.VN[tier].equals(stored)) return Math.max(tier, minimum);
        }
        return defaultVoltageTier(recipeEUt);
    }

    /**
     * Tier for a node with no stored voltage: the chart's minimum, raised to the lowest tier that can
     * run this recipe. An unset node is planned at the chart's tier, and a recipe too expensive for
     * that still gets a working hatch.
     */
    public static int defaultVoltageTier(final long recipeEUt) {
        return Math.max(minimumVoltageTier(recipeEUt), chartMinimum(Settings.VOLTAGE, 0));
    }

    /**
     * Chart's floor for this setting, or {@code best} when the chart sets none. Read from the chart on
     * screen, not passed in: a {@link SettingDef} receives the recipe and the node's settings, never the
     * node or its graph, and only the active chart draws rows.
     */
    private static int chartMinimum(final Settings setting, final int best) {
        return ChartMinimums.floor(setting, best);
    }

    /**
     * Settings a GregTech chart can set a floor for. Registered, not listed in the panel that draws
     * them, so the panel references no mod and works on a pack without GregTech.
     */
    public static void registerChartMinimums() {
        for (final ChartFloor floor : CHART_FLOORS) {
            final ModifierRange range = floor.kind()
                .getRange();
            ChartMinimums.register(
                ChartMinimums.Minimum.strongest(
                    floor.setting(),
                    floor.label(),
                    (int) range.min(),
                    (int) range.max(),
                    floor.kind()::label));
        }
        // one below the top of GregTech's list, matching the voltage row's options
        ChartMinimums.register(
            ChartMinimums.Minimum
                .weakest(Settings.VOLTAGE, "Volt", 0, GTValues.VN.length - 2, tier -> GTValues.VN[tier]));
    }

    /**
     * Structure value with a chart-wide floor, because it follows the world's progression, not one machine. A chart
     * planned at Cupronickel that prints Eternal numbers is wrong on every node.
     *
     * @param label Short enough to fit beside the floor's two steppers
     */
    private record ChartFloor(ModifierKind.IntKind kind, Settings setting, String label) {}

    private static final List<ChartFloor> CHART_FLOORS = List.of(
        new ChartFloor(ModifierKind.COIL, Settings.GT_COIL, "Coil"),
        new ChartFloor(ModifierKind.PIPE_CASING, Settings.GT_PIPE_CASING, "Pipe"));

    /** Voltage options for a recipe of this cost, lowest usable first. */
    @Nonnull
    public static List<String> voltageOptions(final long recipeEUt) {
        final List<String> names = new ArrayList<>();
        for (int tier = minimumVoltageTier(recipeEUt); tier < GTValues.VN.length - 1; tier++) {
            names.add(GTValues.VN[tier]);
        }
        return names;
    }

    /**
     * Switches the node to the raw overclock rows. Also the provenance marker: while set, every stored
     * setting is a user value and no spec value replaces it.
     */
    public static final SettingDef<Boolean> ADVANCED_DEF = SettingDef
        .boolDef(ADVANCED, false, (v, c) -> v ? "A" : null);

    /**
     * Parallels default to the structure's maximum, since players rarely run a multiblock below it.
     * Stored 0 means that maximum, so the number follows the machine and its coils. A stored count
     * would stay at the value from when the node was made.
     */
    public static final SettingDef<Integer> PARALLELS_DEF = SettingDef
        .autoIntDefCapped(Settings.PARALLELS.key(), 1, 4096, 1, GTSettings::machineMaxParallel, (v, c) -> "∥" + v);

    /** Selected machine's max parallels at the node's structure. */
    public static int machineMaxParallel(final RecipeContext ctx, final Map<String, Object> settings) {
        return fromSpec(ctx, settings, 1, ResolvedRecipe::maxParallel);
    }

    /**
     * GregTech's numbers for this node's recipe on its selected machine and structure. Null for a machine without a
     * spec, or a node without a GregTech recipe.
     */
    @Nullable
    public static ResolvedRecipe resolved(final RecipeContext ctx, final Map<String, Object> settings) {
        final Planned planned = planned(ctx, settings);
        return planned == null ? null : planned.resolved();
    }

    /**
     * Node's plan: its machine's inputs and the numbers its recipe resolves to with them.
     *
     * @param floors     Chart floors read for this plan, in {@link #floor(int)} order
     * @param calculator Read by the rows, never modified
     */
    private record Planned(RecipeContext ctx, Map<String, Object> settings, long[] floors,
        GTMachineIndex.MachineEntry entry, ProcessingInputs inputs, @Nullable ResolvedRecipe resolved,
        @Nullable OverclockCalculator calculator) {}

    /**
     * Last node's plan. Every row of a node's panel reads it each frame, and a panel draws its rows together, so one
     * slot is enough, as in {@link GTMachineIndex#candidates}. Every input of the plan is part of the cache key.
     */
    @Nullable
    private static Planned lastPlanned;

    @Nullable
    private static Planned planned(final RecipeContext ctx, final Map<String, Object> settings) {
        final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, settings);
        if (entry == null || entry.machine() == null) return null;
        final Planned last = lastPlanned;
        if (last != null && last.ctx() == ctx
            && last.entry() == entry
            && last.settings()
                .equals(settings)
            && sameFloors(last.floors())) return last;

        final StructureState state = state(ctx, settings, entry);
        final GTRecipe recipe = ctx.getOrDefault(GTProvider.GT_RECIPE, null);
        final ProcessingInputs inputs = recipe == null ? entry.machine()
            .inputs(state)
            : entry.machine()
                .inputs(state, recipe);
        final ResolvedRecipe resolved = recipe == null ? null
            : entry.machine()
                .spec()
                .resolve(recipe, inputs);
        final long[] floors = new long[CHART_FLOORS.size() + 1];
        for (int i = 0; i < floors.length; i++) floors[i] = floor(i);
        lastPlanned = new Planned(
            ctx,
            new HashMap<>(settings),
            floors,
            entry,
            inputs,
            resolved,
            resolved == null ? null : resolved.toCalculator());
        return lastPlanned;
    }

    private static boolean sameFloors(final long[] floors) {
        for (int i = 0; i < floors.length; i++) if (floors[i] != floor(i)) return false;
        return true;
    }

    /** 0 is the voltage floor, then the {@link #CHART_FLOORS}. */
    private static long floor(final int index) {
        if (index == 0) return chartMinimum(Settings.VOLTAGE, 0);
        final ChartFloor floor = CHART_FLOORS.get(index - 1);
        return chartMinimum(
            floor.setting(),
            (int) floor.kind()
                .getRange()
                .max());
    }

    @Nonnull
    private static StructureState state(final RecipeContext ctx, final Map<String, Object> settings,
        final GTMachineIndex.MachineEntry entry) {
        return resolve(ctx, settings, voltageTier(ctx, settings), mode(ctx, entry, settings));
    }

    /**
     * Reads one number off the node's machine at its structure, so an untouched row draws the machine's
     * value, not a global default that is wrong for most machines.
     */
    private static int fromSpec(final RecipeContext ctx, final Map<String, Object> settings, final int fallback,
        final ToIntFunction<ResolvedRecipe> reader) {
        final ResolvedRecipe resolved = resolved(ctx, settings);
        return resolved == null ? fallback : reader.applyAsInt(resolved);
    }

    /**
     * Reads one number off the calculator for this node's recipe on its machine. Each advanced row can store an
     * override for this value.
     */
    private static int fromCalculator(final RecipeContext ctx, final Map<String, Object> settings, final int fallback,
        final ToIntFunction<OverclockCalculator> reader) {
        final Planned planned = planned(ctx, settings);
        return planned == null || planned.calculator() == null ? fallback : reader.applyAsInt(planned.calculator());
    }

    /** Row percentage for a value GregTech stores as a factor. */
    public static int percent(final double factor) {
        return (int) Math.round(100 * factor);
    }

    /** Percentages drawn in the rows. The maths uses the spec's exact doubles, never these. */
    public static final SettingDef<Integer> SPEED_DEF = SettingDef.autoIntDef(
        Settings.SPEED.key(),
        10,
        10000,
        100,
        (ctx, s) -> fromCalculator(ctx, s, 100, c -> percent(1 / c.getDurationModifier())),
        (v, c) -> "⏱" + v + "%");

    public static final SettingDef<Integer> EUT_DISCOUNT_DEF = SettingDef.autoIntDef(
        Settings.EUT_DISCOUNT.key(),
        0,
        100,
        100,
        (ctx, s) -> fromCalculator(ctx, s, 100, c -> percent(c.getEUtDiscount())),
        (v, c) -> "D" + v + "%");

    public static final SettingDef<Integer> EUT_PER_OC_DEF = SettingDef.autoIntDef(
        Settings.EUT_INCREASE_PER_OC.key(),
        100,
        1000,
        400,
        (ctx, s) -> fromCalculator(ctx, s, 400, c -> percent(c.getEUtIncreasePerOC())),
        (v, c) -> "EU×" + (v / 100));

    public static final SettingDef<Integer> DURATION_PER_OC_DEF = SettingDef.autoIntDef(
        Settings.DURATION_DECREASE_PER_OC.key(),
        100,
        1000,
        200,
        (ctx, s) -> fromCalculator(ctx, s, 200, c -> percent(c.getDurationDecreasePerOC())),
        (v, c) -> "Spd×" + (v / 100));

    public static final SettingDef<Integer> MACHINE_HEAT_DEF = SettingDef.autoIntDef(
        Settings.MACHINE_HEAT.key(),
        0,
        100000,
        0,
        (ctx, s) -> fromCalculator(ctx, s, 0, OverclockCalculator::getMachineHeat),
        (v, c) -> "M" + v);

    /** Zero for a machine without heat, since such a recipe's special value stores something else. */
    public static final SettingDef<Integer> RECIPE_HEAT_DEF = SettingDef.autoIntDef(
        Settings.RECIPE_HEAT.key(),
        0,
        100000,
        0,
        (ctx, s) -> fromCalculator(ctx, s, 0, OverclockCalculator::getRecipeHeat),
        (v, c) -> "R" + v);

    /** GT's heat discount base, per 900K of headroom. */
    public static final SettingDef<Integer> HEAT_DISCOUNT_MULT_DEF = SettingDef.autoIntDef(
        Settings.HEAT_DISCOUNT_MULT.key(),
        0,
        200,
        percent(OverclockCalculator.DEFAULT_HEAT_DISCOUNT_MULTIPLIER),
        (ctx, s) -> fromCalculator(
            ctx,
            s,
            percent(OverclockCalculator.DEFAULT_HEAT_DISCOUNT_MULTIPLIER),
            c -> percent(c.getHeatDiscountMultiplier())),
        null);

    /** 0 for a machine that skips no tiers, a valid value and not "unset". */
    public static final SettingDef<Integer> MAX_TIER_SKIPS_DEF = SettingDef.autoIntDef(
        Settings.MAX_TIER_SKIPS.key(),
        0,
        10,
        1,
        (ctx, s) -> fromCalculator(ctx, s, 1, OverclockCalculator::getMaxTierSkips),
        (v, c) -> "Sk" + v);

    public static final SettingDef<Boolean> HEAT_OC_DEF = SettingDef.autoBoolDef(
        Settings.HEAT_OC.key(),
        (ctx, s) -> fromCalculator(ctx, s, 0, c -> c.isHeatOC() ? 1 : 0),
        (v, c) -> v ? "H" : null);

    public static final SettingDef<Boolean> HEAT_DISCOUNT_DEF = SettingDef.autoBoolDef(
        Settings.HEAT_DISCOUNT.key(),
        (ctx, s) -> fromCalculator(ctx, s, 0, c -> c.isHeatDiscount() ? 1 : 0),
        (v, c) -> v ? "D" : null);

    public static final SettingDef<Boolean> UNLIMITED_SKIPS_DEF = SettingDef.autoBoolDef(
        Settings.UNLIMITED_SKIPS.key(),
        (ctx, s) -> fromCalculator(ctx, s, 0, c -> c.getMaxTierSkips() == Integer.MAX_VALUE ? 1 : 0),
        (v, c) -> v ? "∞T" : null);

    /**
     * Default amperage comes from the machine block, not a spec formula. It's a floor, not a ceiling: a
     * multiblock draws whatever its energy hatches supply, so the row must go past the block's amperage.
     */
    public static final SettingDef<Integer> AMP_DEF = SettingDef
        .autoIntDef(Settings.AMP.key(), 1, MAX_AMPERAGE, 1, (ctx, s) -> {
            final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, s);
            return entry == null ? 1 : Math.max(1, entry.amperage());
        }, (v, c) -> "A" + v);

    /**
     * Ceiling comes from the selected machine's mode count. Some GregTech multiblocks have three modes, and a fixed
     * ceiling of one would leave the third unreachable.
     */
    public static final SettingDef<Integer> MODE_DEF = SettingDef.intDef(MODE, 0, 0, GTSettings::modeCeiling);

    private static int modeCeiling(final RecipeContext ctx, final Map<String, Object> settings) {
        final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, settings);
        return entry == null ? 1
            : entry.modes()
                .count() - 1;
    }

    /**
     * Key a structure value of this kind is stored under. GregTech kinds use the bare name, matching charts saved
     * before kinds were namespaced.
     */
    @Nonnull
    public static String structureKey(final ModifierKind kind) {
        final String[] namespaceAndName = kind.id.split(":", 2);
        final String name = namespaceAndName[namespaceAndName.length - 1];
        return namespaceAndName.length == 2 && !namespaceAndName[0].equals("gregtech")
            ? "gt_" + namespaceAndName[0] + "_" + name
            : "gt_" + name;
    }

    /**
     * Row for a structure value: labelled and valued with GregTech's names for the kind, so a coil row draws coil block
     * names, and bounded by the selected machine's range.
     */
    @Nonnull
    public static SettingDef<?> structureDef(final ModifierKind kind) {
        return SettingDef.autoIntDef(structureKey(kind), 0, 0, (ctx, s) -> planned(ctx, s, kind), null)
            .withLabelAndRange(
                kind.getName(),
                (ctx, s) -> (int) declaredRange(ctx, s, kind).min(),
                (ctx, s) -> (int) declaredRange(ctx, s, kind).max())
            .withDisplay(value -> kind.label(Long.parseLong(value)));
    }

    /**
     * Value an untouched row draws, the one the node is planned with: the chart's floor raised to the lowest value that
     * runs the recipe, else the spec's best, per {@link GTMachineSpec#inputs}.
     */
    private static int planned(final RecipeContext ctx, final Map<String, Object> settings, final ModifierKind kind) {
        final Planned planned = planned(ctx, settings);
        if (planned == null) return 0;
        final ProcessingInputs inputs = planned.inputs();
        return switch (kind) {
            case ModifierKind.IntKind tier -> inputs.value(tier);
            case ModifierKind.LongKind amount -> (int) Math.min(Integer.MAX_VALUE, inputs.value(amount));
        };
    }

    @Nonnull
    private static ModifierRange declaredRange(final RecipeContext ctx, final Map<String, Object> settings,
        final ModifierKind kind) {
        final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, settings);
        final ModifierRange range = entry == null ? null
            : entry.structure()
                .get(kind);
        return range == null ? new ModifierRange(kind, 0, 0) : range;
    }

    /**
     * Player's structure values, plus the chart's floors for the other kinds, or the best value where the chart sets no
     * floor. A value stored in a node's row is used for that node in place of the floor.
     */
    @Nonnull
    public static StructureState resolve(final RecipeContext ctx, final Map<String, Object> settings,
        final int voltageTier) {
        return resolve(ctx, settings, voltageTier, MachineProfile.getInt(settings, MODE, 0));
    }

    /**
     * As above, with the mode passed by a caller that already has the machine. Separate so resolving a
     * structure, which a chart does per frame, never reads the machine index.
     */
    @Nonnull
    public static StructureState resolve(final RecipeContext ctx, final Map<String, Object> settings,
        final int voltageTier, final int mode) {
        final Map<ModifierKind, Long> structure = new HashMap<>();
        for (final ModifierKind kind : ModifierKind.all()) {
            final String key = structureKey(kind);
            if (settings.containsKey(key)) structure.put(kind, (long) MachineProfile.getInt(settings, key, 0));
        }
        final Map<ModifierKind, Long> floors = new HashMap<>();
        for (final ChartFloor floor : CHART_FLOORS) {
            floors.put(
                floor.kind(),
                (long) chartMinimum(
                    floor.setting(),
                    (int) floor.kind()
                        .getRange()
                        .max()));
        }
        return new StructureState(
            voltageTier,
            MachineProfile.getInt(settings, Settings.AMP.key(), 1),
            mode,
            structure,
            floors);
    }

    /**
     * Machine mode this node runs in. A controller with two machines behind it runs one per recipemap,
     * and the node's recipe came from one of those recipemaps, so the mode is derived from it. Other
     * machines use the mode row.
     */
    public static int mode(final RecipeContext ctx, @Nullable final GTMachineIndex.MachineEntry entry,
        final Map<String, Object> settings) {
        final int implied = entry == null ? -1 : entry.modeFor(ctx.getOrDefault(GTProvider.RECIPE_MAP, null));
        return implied >= 0 ? implied : MachineProfile.getInt(settings, MODE, 0);
    }

    public static boolean isAdvanced(final Map<String, Object> settings) {
        return MachineProfile.getBool(settings, ADVANCED, false);
    }

    /** Row predicate: true only when the node's selected machine reads the setting. */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> usesSetting(final Settings setting) {
        final BiPredicate<RecipeContext, Map<String, Object>> machineReadsIt = MachineVariants.usesSetting(setting);
        return (ctx, settings) -> {
            // Two GregTech-only conditions: advanced mode replaces these rows with the raw overclock
            // ones, and the mode row is hidden when the mode follows from the recipemap.
            if (isAdvanced(settings)) return false;
            if (setting == Settings.GT_MODE) {
                final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, settings);
                if (entry != null && entry.modeFor(ctx.getOrDefault(GTProvider.RECIPE_MAP, null)) >= 0) return false;
            }
            return machineReadsIt.test(ctx, settings);
        };
    }

    /** Row predicate: true only when the node's selected machine reads that kind. */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> usesStructure(final ModifierKind kind) {
        return (ctx, settings) -> {
            if (isAdvanced(settings)) return false;
            final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, settings);
            return entry != null && entry.structure()
                .containsKey(kind);
        };
    }

    /** Raw overclock rows, visible only in advanced mode. */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> advancedOnly() {
        return (ctx, settings) -> isAdvanced(settings);
    }

    /**
     * The machine is picked from the node's title bar, not a settings row. The def is still in the
     * profile so the choice serializes, but it never draws as a row.
     */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> neverAsARow() {
        return (ctx, settings) -> false;
    }

    /**
     * A singleblock's tier is fixed by the placed block, so voltage is editable only on a multiblock that
     * overclocks. Also visible when no machine resolves, so a node with an unknown machine still has a
     * usable control.
     */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> voltageEditable() {
        return (ctx, settings) -> multiblockOrUnknown(ctx, settings) && overclocks(ctx, settings);
    }

    /**
     * Amperage comes from a multiblock's energy hatches, so it's editable on a multiblock that
     * overclocks. A singleblock's amperage is fixed by its block.
     */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> ampEditable() {
        return (ctx, settings) -> multiblockOrUnknown(ctx, settings) && overclocks(ctx, settings);
    }

    /**
     * A machine that runs every recipe at the recipe's voltage gains nothing from its energy hatches,
     * so their tier and amperage change none of its numbers.
     */
    private static boolean overclocks(final RecipeContext ctx, final Map<String, Object> settings) {
        if (isAdvanced(settings)) return true;
        final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, settings);
        // noOverclock doesn't vary by mode or tier in GregTech, so testing one structure covers all
        return entry == null || entry.machine() == null
            || !(entry.machine()
                .spec()
                .getOverclock(
                    entry.machine()
                        .inputs(StructureState.of(1, 0))) instanceof ProcessingSpec.OverclockRule.None);
    }

    /**
     * The spec gives the machine's maximum, but planning for fewer parallels is common, so the cap is
     * editable wherever the maximum exceeds one. A multiblock that runs one recipe at a time (Large
     * Chemical Reactor, IsaMill) has a maximum of one, so its row would have no other value.
     */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> parallelsEditable() {
        return (ctx, settings) -> multiblockOrUnknown(ctx, settings)
            && (isAdvanced(settings) || machineMaxParallel(ctx, settings) > 1);
    }

    /**
     * Whether the node is a multiblock. The machine picker sets it, but it stays a setting so a node with
     * an unknown machine can still be marked as either form factor. Read through
     * {@link MachineVariant#tieredByBuild()}, not a GregTech type check, so the fact has one source and
     * works for a mod whose build choice is not a hatch.
     */
    public static final SettingDef<Boolean> MULTIBLOCK_DEF = SettingDef
        .autoBoolDef(Settings.GT_MULTIBLOCK.key(), (ctx, s) -> {
            final MachineVariant machine = MachineVariants.selected(ctx, s);
            return machine == null || machine.tieredByBuild() ? 1 : 0;
        }, (v, c) -> v ? "M" : null);

    private static boolean multiblockOrUnknown(final RecipeContext ctx, final Map<String, Object> settings) {
        return isAdvanced(settings) || MULTIBLOCK_DEF.effectiveBool(ctx, settings);
    }

    /**
     * Settings derived from the machine. A chart saved before the machine picker has these tuned by
     * hand, and applying the spec would change its numbers, so such a chart opens in advanced mode.
     * Voltage and machine count are absent because the user sets them in both modes, so a chart that
     * only set "IV, x4" gets the compact UI.
     */
    private static final List<String> DERIVED_KEYS = List.of(
        Settings.AMP.key(),
        Settings.SPEED.key(),
        Settings.PARALLELS.key(),
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

    /**
     * Older charts store the coil row as GregTech's coil level name, not the kind's number. An unknown name is dropped,
     * so the node opens on the chart's coil, not on Cupronickel.
     */
    private static void migrateCoilName(final Map<String, Object> settings) {
        if (!(settings.get(COIL) instanceof final String name)) return;
        settings.remove(COIL);
        for (final HeatingCoilLevel level : HeatingCoilLevel.values()) {
            if (level.name()
                .equals(name)
                && ModifierKind.COIL.getRange()
                    .contains(level.getTier())) {
                settings.put(COIL, (int) level.getTier());
            }
        }
    }

    /** Older charts store perfect overclocking as a flag, which only set the duration factor to 4x. */
    private static void migratePerfectOverclock(final Map<String, Object> settings) {
        if (Boolean.TRUE.equals(settings.remove("perfect_oc"))) {
            settings.put(Settings.DURATION_DECREASE_PER_OC.key(), 400);
        }
    }

    public static void migrateLegacyNode(final Map<String, Object> settings) {
        migrateCoilName(settings);
        migratePerfectOverclock(settings);
        if (settings.containsKey(ADVANCED) || settings.containsKey(MACHINE)) return;
        for (final String key : DERIVED_KEYS) {
            if (settings.containsKey(key)) {
                settings.put(ADVANCED, true);
                return;
            }
        }
    }

}
