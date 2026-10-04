package com.sbancuz.plannh.data.provider.gregtech;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.machine.MachineVariant;
import com.sbancuz.plannh.data.provider.GTProvider;

import gregtech.api.logic.ProcessingRun;
import gregtech.api.logic.ResolvedRecipe;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;

/**
 * GregTech's implementation of {@link MachineVariant#run}: turns "Maceration Stack with HSS-G coils at IV" into a
 * duration, an EU/t draw and a parallel count, from the machine's ProcessingSpec or else GT's describer.
 *
 * <p>
 * A spec comes first, because GregTech checks at load that each machine runs as its spec computes, and a spec models
 * the structure around a multiblock. A describer matches every singleblock at no extra cost.
 */
public final class GTPresetApplier {

    private GTPresetApplier() {}

    /**
     * Machine's result for this recipe, called by {@code Effects.machineDriven}. A recipe GregTech would not start at
     * this structure returns the recipe's numbers unchanged, with GregTech's rejection reason. Null for a machine with
     * neither a spec nor a describer, or a recipe with no energy or duration for a describer to overclock.
     */
    @Nullable
    public static EffectResult run(@Nonnull final GTMachineIndex.MachineEntry entry, final RecipeContext ctx,
        final Map<String, Object> settings, final EffectResult recipe) {
        final int machines = MachineProfile.getInt(settings, Settings.MACHINES.key(), 1);
        final ResolvedRecipe resolved = GTSettings.resolved(ctx, settings);
        if (resolved != null) {
            // an untouched node stores 0, meaning the machine's maximum
            final int userCap = MachineProfile.getInt(settings, Settings.PARALLELS.key(), 0);
            final ResolvedRecipe capped = userCap > 0 ? resolved.capParallel(userCap) : resolved;
            final ProcessingRun run = capped.calculate(applyOverrides(capped.toCalculator(), settings));
            if (!run.result()
                .wasSuccessful())
                return recipe.rejectedBecause(
                    run.result()
                        .getDisplayString());
            return effect(run, machines);
        }

        // singleblock: GregTech's describer, the same calculator the machine and NEI use
        final GTRecipe gtRecipe = ctx.getOrDefault(GTProvider.GT_RECIPE, null);
        if (entry.describer() == null || gtRecipe == null || gtRecipe.mEUt <= 0 || gtRecipe.mDuration <= 0) return null;
        final OverclockCalculator calculator = applyOverrides(
            entry.describer()
                .createCalculator(gtRecipe),
            settings).calculate();
        return new EffectResult(calculator.getDuration(), calculator.getConsumption(), machines);
    }

    /** Node's numbers for a run GregTech computed, at this many machines. */
    @Nonnull
    public static EffectResult effect(final ProcessingRun run, final int machines) {
        return new EffectResult(run.ticks(), run.euPerTick(), run.parallel() * machines)
            .withOutputs(expectedOutput(run.output()), details(run));
    }

    /** Average output per parallel: success chance times yield per success. */
    private static double expectedOutput(final ProcessingRun.Output output) {
        return output.successChance() * output.yield();
    }

    /** Run's costs and gains besides EU/t, and its odds, listed beside the node's numbers. */
    @Nonnull
    private static List<String> details(final ProcessingRun run) {
        final List<String> details = new ArrayList<>();
        if (!run.output()
            .equals(ProcessingRun.Output.CERTAIN)) {
            details.add(
                StatCollector.translateToLocalFormatted(
                    "plannh.gt.output_odds",
                    GTSettings.percent(
                        run.output()
                            .successChance()),
                    GTSettings.percent(
                        run.output()
                            .yield())));
        }
        final ProcessingRun.RunEu eu = run.eu();
        if (eu.startup()
            .signum() != 0) {
            details.add(StatCollector.translateToLocalFormatted("plannh.gt.startup_eu", formatNumber(eu.startup())));
        }
        if (eu.perRun()
            .signum() != 0) {
            details.add(StatCollector.translateToLocalFormatted("plannh.gt.eu_per_run", formatNumber(eu.perRun())));
        }
        if (eu.generated()
            .signum() != 0) {
            details
                .add(StatCollector.translateToLocalFormatted("plannh.gt.eu_generated", formatNumber(eu.generated())));
        }
        return details;
    }

    /**
     * Lays the user's numbers over the machine's. Only stored keys are applied. The map is sparse, so an
     * absent key leaves the machine's value at full precision, not the rounded percentage drawn in the
     * advanced rows. An unreadable stored value falls back to the machine's. Ticking Advanced changes no
     * number until a row is edited.
     */
    @Nonnull
    public static OverclockCalculator applyOverrides(final OverclockCalculator calculator,
        final Map<String, Object> settings) {
        if (settings.containsKey(Settings.AMP.key())) {
            calculator.setAmperage(
                MachineProfile.getInt(settings, Settings.AMP.key(), (int) calculator.getMachineAmperage()));
        }
        if (settings.containsKey(Settings.SPEED.key())) {
            calculator.setDurationModifier(
                100.0 / Math.max(
                    1,
                    MachineProfile.getInt(
                        settings,
                        Settings.SPEED.key(),
                        GTSettings.percent(1 / calculator.getDurationModifier()))));
        }
        if (settings.containsKey(Settings.EUT_DISCOUNT.key())) {
            calculator.setEUtDiscount(
                MachineProfile
                    .getInt(settings, Settings.EUT_DISCOUNT.key(), GTSettings.percent(calculator.getEUtDiscount()))
                    / 100.0);
        }
        if (settings.containsKey(Settings.EUT_INCREASE_PER_OC.key())) {
            calculator.setEUtIncreasePerOC(
                MachineProfile.getInt(
                    settings,
                    Settings.EUT_INCREASE_PER_OC.key(),
                    GTSettings.percent(calculator.getEUtIncreasePerOC())) / 100.0);
        }
        if (settings.containsKey(Settings.DURATION_DECREASE_PER_OC.key())) {
            calculator.setDurationDecreasePerOC(
                MachineProfile.getInt(
                    settings,
                    Settings.DURATION_DECREASE_PER_OC.key(),
                    GTSettings.percent(calculator.getDurationDecreasePerOC())) / 100.0);
        }
        if (settings.containsKey(Settings.NO_OVERCLOCK.key())) {
            calculator.setNoOverclock(
                MachineProfile.getBool(settings, Settings.NO_OVERCLOCK.key(), calculator.isNoOverclock()));
        }
        if (settings.containsKey(Settings.LASER_OC.key())) {
            calculator.setLaserOC(MachineProfile.getBool(settings, Settings.LASER_OC.key(), calculator.isLaserOC()));
        }
        if (settings.containsKey(Settings.MAX_OVERCLOCKS.key())) {
            calculator.setMaxOverclocks(
                MachineProfile.getInt(settings, Settings.MAX_OVERCLOCKS.key(), calculator.getMaxOverclocks()));
        }
        if (settings.containsKey(Settings.MAX_REGULAR_OC.key())) {
            calculator.setMaxRegularOverclocks(
                MachineProfile.getInt(settings, Settings.MAX_REGULAR_OC.key(), calculator.getMaxRegularOverclocks()));
        }
        if (settings.containsKey(Settings.UNLIMITED_SKIPS.key())
            && MachineProfile.getBool(settings, Settings.UNLIMITED_SKIPS.key(), false)) {
            calculator.setUnlimitedTierSkips();
        } else if (settings.containsKey(Settings.MAX_TIER_SKIPS.key())) {
            // zero is a valid cap here, not "unset": a present key is a user value
            calculator.setMaxTierSkips(
                MachineProfile.getInt(settings, Settings.MAX_TIER_SKIPS.key(), calculator.getMaxTierSkips()));
        }
        if (settings.containsKey(Settings.MACHINE_HEAT.key())) {
            calculator.setMachineHeat(
                MachineProfile.getInt(settings, Settings.MACHINE_HEAT.key(), calculator.getMachineHeat()));
        }
        if (settings.containsKey(Settings.RECIPE_HEAT.key())) {
            calculator
                .setRecipeHeat(MachineProfile.getInt(settings, Settings.RECIPE_HEAT.key(), calculator.getRecipeHeat()));
        }
        if (settings.containsKey(Settings.HEAT_OC.key())) {
            calculator.setHeatOC(MachineProfile.getBool(settings, Settings.HEAT_OC.key(), calculator.isHeatOC()));
        }
        if (settings.containsKey(Settings.HEAT_DISCOUNT.key())) {
            calculator.setHeatDiscount(
                MachineProfile.getBool(settings, Settings.HEAT_DISCOUNT.key(), calculator.isHeatDiscount()));
        }
        if (settings.containsKey(Settings.HEAT_DISCOUNT_MULT.key())) {
            calculator.setHeatDiscountMultiplier(
                MachineProfile.getInt(
                    settings,
                    Settings.HEAT_DISCOUNT_MULT.key(),
                    GTSettings.percent(calculator.getHeatDiscountMultiplier())) / 100.0);
        }
        return calculator;
    }
}
