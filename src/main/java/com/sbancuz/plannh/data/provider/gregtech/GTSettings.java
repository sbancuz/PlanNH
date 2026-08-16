package com.sbancuz.plannh.data.provider.gregtech;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.ToIntBiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.steps.GTOverclockStep;
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
    public static final String COIL = "gt_coil";
    public static final String SOLENOID = "gt_solenoid";
    public static final String ITEM_PIPE = "gt_item_pipe";
    public static final String PIPE_CASING = "gt_pipe_casing";
    public static final String SAWBLADE = "gt_sawblade";
    public static final String ELECTRODE = "gt_electrode";
    public static final String STRUCTURE_TIER = "gt_structure_tier";
    public static final String WIDTH = "gt_width";
    public static final String MODE = "gt_mode";

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
     * "off": a GT node always draws power. Unset resolves to the first option, which is the minimum,
     * so a fresh node already reads as the un-overclocked recipe rather than as nothing.
     */
    public static final SettingDef<String> VOLTAGE_DEF = SettingDef
        .dynamicEnumDef(Settings.VOLTAGE.key(), "", GTSettings::voltageOptions, name -> name, (v, c) -> v);

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
        return minimum;
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
        .autoIntDef(Settings.PARALLELS.key(), 1, 4096, GTSettings::machineMaxParallel, (v, c) -> "∥" + v);

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
        (ctx, s) -> fromPreset(
            ctx,
            s,
            0,
            (p, st) -> p.machineHeat()
                .applyAsInt(st)),
        (v, c) -> "M" + v);

    public static final SettingDef<Integer> RECIPE_HEAT_DEF = SettingDef
        .autoIntDef(Settings.RECIPE_HEAT.key(), 0, 100000, (ctx, s) -> {
            final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, s);
            return GTPresetApplier.recipeHeat(ctx, entry == null ? null : entry.preset());
        }, (v, c) -> "R" + v);

    /** GT's own heat discount base, 0.95 per 900K of headroom. */
    public static final SettingDef<Integer> HEAT_DISCOUNT_MULT_DEF = SettingDef
        .autoIntDef(Settings.HEAT_DISCOUNT_MULT.key(), 0, 200, (ctx, s) -> 95, null);

    /**
     * A machine that skips no tiers reports 0, which is a real answer. -1 on the preset means the
     * machine never asked, leaving GT's own default of one.
     */
    public static final SettingDef<Integer> MAX_TIER_SKIPS_DEF = SettingDef.autoIntDef(
        Settings.MAX_TIER_SKIPS.key(),
        0,
        10,
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

    /** Amperage comes from the machine block itself rather than from a preset formula. */
    public static final SettingDef<Integer> AMP_DEF = SettingDef.autoIntDef(Settings.AMP.key(), 1, 64, (ctx, s) -> {
        final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, s);
        return entry == null ? 1 : Math.max(1, entry.amperage());
    }, (v, c) -> "A" + v);

    public static final SettingDef<String> COIL_DEF = SettingDef
        .enumDef(COIL, COIL_NAMES.getLast(), COIL_NAMES, (v, c) -> null);
    public static final SettingDef<Integer> SOLENOID_DEF = SettingDef.intDef(
        SOLENOID,
        GTStructureTiers.MAX_SOLENOID_TIER,
        GTStructureTiers.MIN_SOLENOID_TIER,
        GTStructureTiers.MAX_SOLENOID_TIER);
    public static final SettingDef<Integer> ITEM_PIPE_DEF = SettingDef
        .intDef(ITEM_PIPE, GTStructureTiers.MAX_ITEM_PIPE_TIER, 1, GTStructureTiers.MAX_ITEM_PIPE_TIER);
    public static final SettingDef<Integer> PIPE_CASING_DEF = SettingDef
        .intDef(PIPE_CASING, GTStructureTiers.MAX_PIPE_CASING_TIER, 1, GTStructureTiers.MAX_PIPE_CASING_TIER);
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
     * The range a knob offers, read off the row that offers it. Anything that varies a knob - the
     * probe's sensitivity scan - then covers exactly what the player can reach, and one edit to a row
     * moves both.
     */
    @Nonnull
    public static TierRange knobRange(final GTMachinePreset.Knob knob) {
        return switch (knob) {
            // The coil row stores a name rather than a number, so its range is the name list.
            case COIL -> new TierRange(0, COIL_NAMES.size() - 1);
            case SOLENOID -> rangeOf(SOLENOID_DEF);
            case ITEM_PIPE -> rangeOf(ITEM_PIPE_DEF);
            case PIPE_CASING -> rangeOf(PIPE_CASING_DEF);
            case SAWBLADE -> rangeOf(SAWBLADE_DEF);
            case ELECTRODE -> rangeOf(ELECTRODE_DEF);
            case STRUCTURE_TIER -> rangeOf(STRUCTURE_TIER_DEF);
            case WIDTH -> rangeOf(WIDTH_DEF);
            // MODE_DEF's own range is per machine, so it says nothing useful here. A sweep over modes
            // takes its count from the machine instead - see GTMachineModes.
            case MODE -> new TierRange(0, 1);
        };
    }

    @Nonnull
    private static TierRange rangeOf(final SettingDef<Integer> def) {
        return new TierRange(def.minInt, def.maxInt);
    }

    /**
     * Structure knobs default to the best available: a planning tool should open on the endgame
     * number, and the row is right there to lower it.
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
            COIL_NAMES.indexOf(MachineProfile.getString(settings, COIL, COIL_NAMES.getLast())),
            MachineProfile.getInt(settings, SOLENOID, GTStructureTiers.MAX_SOLENOID_TIER),
            MachineProfile.getInt(settings, ITEM_PIPE, GTStructureTiers.MAX_ITEM_PIPE_TIER),
            MachineProfile.getInt(settings, PIPE_CASING, GTStructureTiers.MAX_PIPE_CASING_TIER),
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

    /** Whether this node's recipe settles the mode, leaving nothing to ask. */
    private static boolean modeIsImplied(final RecipeContext ctx, final Map<String, Object> settings) {
        final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, settings);
        return entry != null && entry.modeFor(ctx.getOrDefault(GTProvider.RECIPE_MAP, null)) >= 0;
    }

    public static boolean isAdvanced(final Map<String, Object> settings) {
        return MachineProfile.getBool(settings, ADVANCED, false);
    }

    /** Shows a knob only when the machine the node selected actually reads it. */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> usesKnob(final GTMachinePreset.Knob knob) {
        return (ctx, settings) -> {
            if (isAdvanced(settings)) return false;
            // No row for a question the recipe has already answered.
            if (knob == GTMachinePreset.Knob.MODE && modeIsImplied(ctx, settings)) return false;
            final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, settings);
            return entry != null && entry.preset() != null
                && entry.preset()
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
     * The preset already gives the machine's maximum, but planning for fewer than the structure
     * allows is normal, so the cap stays editable wherever it can exceed one.
     */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> parallelsEditable() {
        return (ctx, settings) -> multiblockOrUnknown(ctx, settings);
    }

    private static boolean multiblockOrUnknown(final RecipeContext ctx, final Map<String, Object> settings) {
        if (isAdvanced(settings)) return true;
        final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, settings);
        return entry == null || entry.kind() == GTMachineIndex.Kind.MULTIBLOCK;
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
