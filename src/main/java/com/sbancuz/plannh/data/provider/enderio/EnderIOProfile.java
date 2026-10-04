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
 * The EnderIO node model, separate from the provider that reads the recipes.
 *
 * <p>
 * The provider references NEI's handler classes, and loading those loads LWJGL. This class has only
 * the model, so its arithmetic can be tested against EnderIO's numbers without a display.
 */
public final class EnderIOProfile {

    private EnderIOProfile() {}

    public static final String ID = "enderio";

    /**
     * The capacitor the machine is built with, which alone sets its speed: a task advances by
     * {@code getPowerUsePerTick} each tick, which is the capacitor's extract rate. An untouched row
     * follows the chart's floor, so one chart-wide choice applies to every EnderIO node.
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
     * Duration and power draw, both from the capacitor. A field separate from the profile so tests can
     * run it headless: building a profile loads NEI's config for the burnable-override row, and NEI
     * loads LWJGL.
     */
    public static final EffectComputer EFFECT = Effects.durationFromFormula(EnderIOProfile::durationTicks)
        .withCostPerT(CoFHCompat.RF_PER_T, (current, s, ctx) -> energy(ctx) == 0 ? 0L : (long) rfPerTick(ctx, s))
        .applyParallelism();

    /**
     * Built per call, not stored in a field: a profile reads NEI's config while it is built, and this
     * class loads long before a screen exists.
     */
    @Nonnull
    public static MachineProfile profile() {
        return MachineProfile.builder(ID, "EnderIO")
            .setting(MachineVariants.pickerDef())
            .setting(Settings.MACHINES.def())
            .setting(Settings.TICK_MODIFIER.def())
            .setting(CAPACITOR_DEF.withVisibility(MachineVariants.usesSetting(Settings.EIO_CAPACITOR)))
            // A recipe stores its energy, not its duration. Duration depends on the capacitor. Reading
            // a duration off the recipe list would fix it at the capacitor in use when the node was made.
            // The total energy is taken from the recipe unchanged, since a capacitor does not change it.
            .effect(EFFECT)
            .build();
    }

    /** Registers the capacitor as a chart floor, so it is chosen once per chart. */
    public static void registerChartMinimum() {
        ChartMinimums.register(
            ChartMinimums.Minimum.strongest(
                Settings.EIO_CAPACITOR,
                "Cap",
                0,
                EnderIOCapacitors.highestTier(),
                EnderIOCapacitors::label));
    }

    /**
     * The recipe's energy divided by the capacitor's RF/t, which is all of EnderIO's timing model. A
     * named method, not inline in the effect chain, so tests can call it: running the chain loads
     * RecipePropertyAPI, whose static initializer builds a FluidStack and so requires a game with
     * registered fluids.
     */
    public static int durationTicks(@Nonnull final RecipeContext ctx, final Map<String, Object> settings) {
        return (int) (energy(ctx) / rfPerTick(ctx, settings));
    }

    /** The RF/t the node's capacitor supplies to a recipe. */
    public static int rfPerTick(@Nonnull final RecipeContext ctx, final Map<String, Object> settings) {
        return EnderIOCapacitors.rfPerTick(CAPACITOR_DEF.effectiveInt(ctx, settings));
    }

    /**
     * The recipe's total energy. Zero for the Enchanter, which spends experience levels, so its RF/t is
     * set to 0 as well.
     */
    private static long energy(@Nonnull final RecipeContext ctx) {
        final Object total = ctx.properties()
            .get(CoFHCompat.RF_COST);
        return total instanceof final Number n ? n.longValue() : 0L;
    }
}
