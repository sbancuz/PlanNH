package com.sbancuz.plannh.data.provider.enderio;

import java.util.Map;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.data.ChartMinimums;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.EffectComputer;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.effect.steps.CoFHCompat;
import com.sbancuz.plannh.data.machine.MachineVariants;

/**
 * What an EnderIO node computes, kept apart from the provider that reads the recipes.
 *
 * <p>
 * The provider has to name NEI's handler classes, and loading those reaches LWJGL; this holds only
 * the model, so the arithmetic can be tested against EnderIO's own numbers without a display.
 */
public final class EnderIOProfile {

    private EnderIOProfile() {}

    public static final String ID = "enderio";

    /**
     * The capacitor the machine is built with, which is the only thing that decides how fast it runs:
     * a task advances by {@code getPowerUsePerTick} each tick, and that is the capacitor's own extract
     * rate. An untouched row follows the chart, so one chart-wide choice covers every EnderIO node.
     */
    public static final SettingDef<Integer> CAPACITOR_DEF = SettingDef
        .autoIntDef(
            Settings.EIO_CAPACITOR.key(),
            0,
            EnderIOCapacitors.highestTier(),
            (ctx, s) -> ChartMinimums.floor(Settings.EIO_CAPACITOR, EnderIOCapacitors.highestTier()),
            null)
        .withDisplay(tier -> EnderIOCapacitors.label(Integer.parseInt(tier)));

    /**
     * Duration and power draw, both a function of the capacitor. Held apart from the profile so it can
     * be exercised without one: building a profile reaches NEI's config for the burnable-override row,
     * and NEI reaches LWJGL, neither of which a headless check of this arithmetic should need.
     */
    public static final EffectComputer EFFECT = Effects.durationFromFormula(EnderIOProfile::durationTicks)
        .withCostPerT(CoFHCompat.RF_PER_T, (current, s, ctx) -> energy(ctx) == 0 ? 0L : (long) rfPerTick(ctx, s))
        .applyParallelism();

    /**
     * Built on demand rather than held in a field, because a profile reads NEI's config as it is
     * assembled and this class is loaded long before a screen exists.
     */
    @Nonnull
    public static MachineProfile profile() {
        return MachineProfile.builder(ID, "EnderIO")
            .setting(MachineVariants.pickerDef())
            .setting(Settings.MACHINES.def())
            .setting(Settings.TICK_MODIFIER.def())
            .setting(CAPACITOR_DEF.withVisibility(MachineVariants.usesKnob(Settings.EIO_CAPACITOR)))
            // A recipe carries the energy it needs, not how long it takes; how long is the capacitor's
            // business. Reading a duration off the recipe list would freeze whichever capacitor
            // happened to be assumed when the node was made. The total is left as the recipe stated it
            // - it is the one number here that a capacitor does not change.
            .effect(EFFECT)
            .build();
    }

    /** The floor every EnderIO node opens at, so the capacitor is chosen once for a whole chart. */
    public static void registerChartMinimum() {
        ChartMinimums.register(
            new ChartMinimums.Minimum(
                Settings.EIO_CAPACITOR,
                "Cap",
                0,
                EnderIOCapacitors.highestTier(),
                EnderIOCapacitors.highestTier(),
                EnderIOCapacitors::label));
    }

    /**
     * How long the recipe takes: its energy divided by what the capacitor puts in each tick, which is
     * the whole of EnderIO's timing model. Named rather than left inline in the effect chain so it can
     * be checked directly - running the chain touches RecipePropertyAPI, whose static initializer
     * builds a FluidStack and so needs a game that has registered its fluids.
     */
    public static int durationTicks(@Nonnull final RecipeContext ctx, final Map<String, Object> settings) {
        return (int) (energy(ctx) / rfPerTick(ctx, settings));
    }

    /** How much energy reaches a recipe each tick, given the capacitor this node is planning with. */
    public static int rfPerTick(@Nonnull final RecipeContext ctx, final Map<String, Object> settings) {
        return EnderIOCapacitors.rfPerTick(CAPACITOR_DEF.effectiveInt(ctx, settings));
    }

    /**
     * What the recipe costs in total. Zero for the Enchanter, which spends experience levels rather
     * than power, and which must therefore not be quoted an RF rate it never draws.
     */
    private static long energy(@Nonnull final RecipeContext ctx) {
        final Object total = ctx.properties()
            .get(CoFHCompat.RF_COST);
        return total instanceof final Number n ? n.longValue() : 0L;
    }
}
