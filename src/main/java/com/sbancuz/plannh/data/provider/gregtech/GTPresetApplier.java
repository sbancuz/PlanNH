package com.sbancuz.plannh.data.provider.gregtech;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.provider.GTProvider;

import gregtech.api.enums.GTValues;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;

/**
 * Turns "this node is a Maceration Stack with HSS-G coils at IV" into a configured
 * {@link OverclockCalculator}. The single place a machine-driven calculator is built, so the three
 * sources of truth - GT's own describer, the preset table, and hand-entered settings - are chosen
 * between once rather than at each call site.
 *
 * <p>
 * Priority is GT's code first: a machine that publishes an
 * {@link gregtech.api.objects.overclockdescriber.OverclockDescriber} gets asked directly, which
 * covers every singleblock, steam machine and fusion reactor exactly and for free. Only multiblocks,
 * whose behaviour depends on blocks a prototype cannot report, fall through to the preset table.
 */
public final class GTPresetApplier {

    private GTPresetApplier() {}

    /** A configured calculator plus the parallel count it was built for. */
    public record Configured(OverclockCalculator calculator, int parallels, long recipeEUt, int duration) {}

    /**
     * @return null when no machine resolves, which leaves the caller on its existing settings path.
     */
    @Nullable
    public static Configured configure(final Map<String, Object> settings, final RecipeContext ctx,
        final long recipeEUt, final int duration) {
        final GTMachineIndex.MachineEntry entry = GTMachineIndex.selected(ctx, settings);
        if (entry == null) return null;

        // A singleblock's tier is fixed by the block itself; only a multiblock's energy hatch is a
        // choice, so only there does the voltage row mean anything.
        final int voltageTier = entry.kind() == GTMachineIndex.Kind.SINGLEBLOCK ? entry.voltageTier()
            : GTSettings.voltageTier(ctx, settings);

        final GTMachinePreset preset = entry.preset();
        final StructureState state = GTSettings
            .resolve(ctx, settings, voltageTier, GTSettings.mode(ctx, entry, settings));

        long eut = recipeEUt;
        int recipeDuration = duration;
        if (preset != null && preset.recipeOverride() != null) {
            eut = preset.recipeOverride()
                .eut();
            recipeDuration = preset.recipeOverride()
                .duration();
        }

        final int parallels = resolveParallels(settings, preset, state);
        final long machineVoltage = GTValues.V[Math.min(voltageTier, GTValues.V.length - 1)];

        final OverclockCalculator calculator = entry.describer() != null
            ? fromDescriber(ctx, entry, eut, recipeDuration)
            : buildFromPreset(
                preset,
                state,
                eut,
                recipeDuration,
                machineVoltage,
                entry.amperage(),
                recipeHeat(ctx, preset));

        applyOverrides(calculator, settings);
        calculator.setParallel(parallels)
            .setAmperageOC(true);
        return new Configured(calculator, parallels, eut, recipeDuration);
    }

    /**
     * GT's own behaviour for this machine. The template is deliberately minimal: SteamOverclockDescriber
     * and EUNoOverclockDescriber ignore it entirely and rebuild from the recipe, so anything set here
     * would be silently dropped for exactly the machines that need it most.
     */
    @Nonnull
    private static OverclockCalculator fromDescriber(final RecipeContext ctx, final GTMachineIndex.MachineEntry entry,
        final long recipeEUt, final int duration) {
        final GTRecipe recipe = ctx.getOrDefault(GTProvider.GT_RECIPE, null);
        final OverclockCalculator template = new OverclockCalculator().setRecipeEUt(recipeEUt)
            .setDuration(duration);
        if (recipe == null) return template.setEUt(GTValues.V[Math.min(entry.voltageTier(), GTValues.V.length - 1)]);
        return entry.describer()
            .createCalculator(template, recipe);
    }

    /**
     * Lays the user's own numbers over the machine's. Only keys actually stored are applied - the map
     * is sparse, so anything absent stays exactly as the machine computed it, at full precision
     * rather than the rounded percentage the advanced rows display.
     *
     * <p>
     * This is what makes Advanced an override rather than a separate world: ticking it does not
     * change a single number until a row is edited.
     */
    public static void applyOverrides(final OverclockCalculator calculator, final Map<String, Object> settings) {
        if (settings.containsKey(Settings.AMP.key())) {
            calculator.setAmperage(MachineProfile.getInt(settings, Settings.AMP.key(), 1));
        }
        if (settings.containsKey(Settings.SPEED.key())) {
            calculator
                .setDurationModifier(100.0 / Math.max(1, MachineProfile.getInt(settings, Settings.SPEED.key(), 100)));
        }
        if (settings.containsKey(Settings.EUT_DISCOUNT.key())) {
            calculator.setEUtDiscount(MachineProfile.getInt(settings, Settings.EUT_DISCOUNT.key(), 100) / 100.0);
        }
        if (settings.containsKey(Settings.EUT_INCREASE_PER_OC.key())) {
            calculator
                .setEUtIncreasePerOC(MachineProfile.getInt(settings, Settings.EUT_INCREASE_PER_OC.key(), 400) / 100.0);
        }
        if (settings.containsKey(Settings.DURATION_DECREASE_PER_OC.key())) {
            calculator.setDurationDecreasePerOC(
                MachineProfile.getInt(settings, Settings.DURATION_DECREASE_PER_OC.key(), 200) / 100.0);
        }
        if (settings.containsKey(Settings.PERFECT_OC.key())
            && MachineProfile.getBool(settings, Settings.PERFECT_OC.key(), false)) {
            calculator.enablePerfectOC();
        }
        if (settings.containsKey(Settings.NO_OVERCLOCK.key())) {
            calculator.setNoOverclock(MachineProfile.getBool(settings, Settings.NO_OVERCLOCK.key(), false));
        }
        if (settings.containsKey(Settings.LASER_OC.key())) {
            calculator.setLaserOC(MachineProfile.getBool(settings, Settings.LASER_OC.key(), false));
        }
        if (settings.containsKey(Settings.MAX_OVERCLOCKS.key())) {
            calculator.setMaxOverclocks(MachineProfile.getInt(settings, Settings.MAX_OVERCLOCKS.key(), 0));
        }
        if (settings.containsKey(Settings.MAX_REGULAR_OC.key())) {
            calculator.setMaxRegularOverclocks(MachineProfile.getInt(settings, Settings.MAX_REGULAR_OC.key(), 0));
        }
        if (settings.containsKey(Settings.UNLIMITED_SKIPS.key())
            && MachineProfile.getBool(settings, Settings.UNLIMITED_SKIPS.key(), false)) {
            calculator.setUnlimitedTierSkips();
        } else if (settings.containsKey(Settings.MAX_TIER_SKIPS.key())) {
            // Zero is a real answer here, not "unset" - presence is what says the user meant it.
            calculator.setMaxTierSkips(MachineProfile.getInt(settings, Settings.MAX_TIER_SKIPS.key(), 1));
        }
        if (settings.containsKey(Settings.MACHINE_HEAT.key())) {
            calculator.setMachineHeat(MachineProfile.getInt(settings, Settings.MACHINE_HEAT.key(), 0));
        }
        if (settings.containsKey(Settings.RECIPE_HEAT.key())) {
            calculator.setRecipeHeat(MachineProfile.getInt(settings, Settings.RECIPE_HEAT.key(), 0));
        }
        if (settings.containsKey(Settings.HEAT_OC.key())) {
            calculator.setHeatOC(MachineProfile.getBool(settings, Settings.HEAT_OC.key(), true));
        }
        if (settings.containsKey(Settings.HEAT_DISCOUNT.key())) {
            calculator.setHeatDiscount(MachineProfile.getBool(settings, Settings.HEAT_DISCOUNT.key(), false));
        }
        if (settings.containsKey(Settings.HEAT_DISCOUNT_MULT.key())) {
            calculator.setHeatDiscountMultiplier(
                MachineProfile.getInt(settings, Settings.HEAT_DISCOUNT_MULT.key(), 95) / 100.0);
        }
    }

    /**
     * The preset branch on its own, so it can be exercised without a populated GT machine registry.
     * Callers still apply parallel and amperage OC afterwards, as {@link #configure} does.
     */
    @Nonnull
    public static OverclockCalculator buildFromPreset(@Nullable final GTMachinePreset preset,
        final StructureState state, final long recipeEUt, final int duration, final long machineVoltage,
        final long amperage, final int recipeHeat) {
        final OverclockCalculator calculator = new OverclockCalculator().setRecipeEUt(recipeEUt)
            .setDuration(duration)
            .setEUt(machineVoltage)
            .setAmperage(amperage);
        if (preset == null) return calculator;

        calculator.setDurationModifier(
            preset.durationModifier()
                .applyAsDouble(state))
            .setEUtDiscount(
                preset.euModifier()
                    .applyAsDouble(state))
            .setEUtIncreasePerOC(
                preset.eutIncreasePerOC()
                    .applyAsDouble(state))
            .setDurationDecreasePerOC(
                preset.durationDecreasePerOC()
                    .applyAsDouble(state));

        if (preset.usesHeat()) {
            calculator.setMachineHeat(
                preset.machineHeat()
                    .applyAsInt(state))
                .setRecipeHeat(recipeHeat)
                .setHeatOC(preset.heatOC())
                .setHeatDiscount(preset.heatDiscount());
        }

        if (preset.unlimitedTierSkips()) {
            calculator.setUnlimitedTierSkips();
        } else if (preset.maxTierSkips() != GTMachinePreset.TIER_SKIPS_UNSET) {
            calculator.setMaxTierSkips(preset.maxTierSkips());
        }
        return calculator;
    }

    /**
     * The recipe's own heat requirement, which GT stores in mSpecialValue. A preset may pin it -
     * the Industrial Alloy Smelter overclocks against a floor of 0 with its coil heat doubled.
     */
    public static int recipeHeat(final RecipeContext ctx, @Nullable final GTMachinePreset preset) {
        if (preset != null && preset.recipeHeatOverride() != GTMachinePreset.RECIPE_HEAT_FROM_RECIPE) {
            return preset.recipeHeatOverride();
        }
        final Integer special = ctx.getOrDefault(GTProvider.SPECIAL_VALUE, null);
        return special != null ? special : 0;
    }

    /**
     * The machine's own maximum, unless the user capped it lower. 0 is what an untouched node stores,
     * and it means the maximum - a node must not silently run at one parallel because a default of 1
     * looked like a deliberate cap.
     */
    private static int resolveParallels(final Map<String, Object> settings, @Nullable final GTMachinePreset preset,
        final StructureState state) {
        final int fromPreset = preset == null ? 1
            : Math.max(
                1,
                preset.maxParallel()
                    .applyAsInt(state));
        final int userCap = MachineProfile.getInt(settings, Settings.PARALLELS.key(), 0);
        return userCap > 0 ? Math.min(userCap, fromPreset) : fromPreset;
    }

}
