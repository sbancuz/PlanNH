package com.sbancuz.plannh.data.provider.gregtech;

import com.gtnewhorizons.angelica.shadow.javax.annotation.Nonnull;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.Settings;
import gregtech.api.util.OverclockCalculator;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.sbancuz.plannh.data.provider.gregtech.GTProvider.EU_PER_TICK;
import static com.sbancuz.plannh.data.provider.gregtech.GTProvider.TOTAL_EU;

public class GTEffect implements MachineProfile.EffectComputer {

    private boolean forceHeat;
    private boolean forceLaserOC;
    private boolean forcePerfectOC;

    @Nonnull
    public GTEffect withHeat() {
        this.forceHeat = true;
        return this;
    }

    @Nonnull
    public GTEffect withPerfectOC() {
        this.forcePerfectOC = true;
        return this;
    }

    @Nonnull
    public GTEffect withLaserOC() {
        this.forceLaserOC = true;
        return this;
    }

    @Override
    @Nonnull
    public MachineProfile.EffectResult compute(final @NotNull Map<String, Object> s, final MachineProfile.@NotNull RecipeContext ctx) {
        final int parallels = MachineProfile.getInt(s, Settings.PARALLELS.key(), 1);
        final int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);

        if (recipeEUt(ctx) <= 0 || ctx.recipeDuration() <= 0
            || MachineProfile.getString(s, Settings.VOLTAGE.key(), "OFF")
            .equals("OFF")) {
            return new MachineProfile.EffectResult(ctx.recipeDuration(), recipeEUt(ctx), parallels * machines);
        }

        final OverclockCalculator calc = buildGtCalc(s, ctx);

        if (forcePerfectOC) calc.enablePerfectOC();
        if (forceLaserOC) calc.setLaserOC(true);

        if (forceHeat) {
            final int machineHeat = MachineProfile.getInt(s, Settings.MACHINE_HEAT.key(), 0);
            if (MachineProfile.getBool(s, Settings.HEAT_OC.key(), true) && machineHeat > 0) {
                final int recipeHeat = MachineProfile.getInt(s, Settings.RECIPE_HEAT.key(), 0);
                calc.setHeatOC(true)
                    .setRecipeHeat(recipeHeat > 0 ? recipeHeat : machineHeat)
                    .setMachineHeat(machineHeat);
                if (MachineProfile.getBool(s, Settings.HEAT_DISCOUNT.key(), false)) calc.setHeatDiscount(true);
                final int hdMult = MachineProfile.getInt(s, Settings.HEAT_DISCOUNT_MULT.key(), 100);
                if (hdMult != 100) calc.setHeatDiscountMultiplier(hdMult / 100.0);
            }
        }

        calc.calculate();
        return new MachineProfile.EffectResult(calc.getDuration(), calc.getConsumption(), parallels * machines);
    }

    @Nonnull
    private static OverclockCalculator buildGtCalc(final Map<String, Object> s,
                                           final MachineProfile.RecipeContext ctx) {
        final long voltage = MachineProfile
            .tierNameToVoltage(MachineProfile.getString(s, Settings.VOLTAGE.key(), "OFF"));
        final long amp = MachineProfile.getInt(s, Settings.AMP.key(), 1);
        final int speed = MachineProfile.getInt(s, Settings.SPEED.key(), 100);
        final int parallels = MachineProfile.getInt(s, Settings.PARALLELS.key(), 1);

        final OverclockCalculator calc = new OverclockCalculator().setRecipeEUt(recipeEUt(ctx))
            .setEUt(voltage)
            .setDuration(ctx.recipeDuration())
            .setAmperage(amp)
            .setDurationModifier(100.0 / speed)
            .setParallel(parallels)
            .setAmperageOC(true);

        if (MachineProfile.getBool(s, Settings.PERFECT_OC.key(), false)) calc.enablePerfectOC();
        if (MachineProfile.getBool(s, Settings.LASER_OC.key(), false)) calc.setLaserOC(true);
        if (MachineProfile.getBool(s, Settings.NO_OVERCLOCK.key(), false)) calc.setNoOverclock(true);

        final int eutDisc = MachineProfile.getInt(s, Settings.EUT_DISCOUNT.key(), 0);
        if (eutDisc > 0) calc.setEUtDiscount(eutDisc / 100.0);

        final int ocMult = MachineProfile.getInt(s, Settings.EUT_INCREASE_PER_OC.key(), 400);
        if (ocMult != 400) calc.setEUtIncreasePerOC(ocMult / 100.0);

        final int durMult = MachineProfile.getInt(s, Settings.DURATION_DECREASE_PER_OC.key(), 200);
        if (durMult != 200) calc.setDurationDecreasePerOC(durMult / 100.0);

        final int maxOc = MachineProfile.getInt(s, Settings.MAX_OVERCLOCKS.key(), 0);
        if (maxOc > 0) calc.setMaxOverclocks(maxOc);

        final int maxReg = MachineProfile.getInt(s, Settings.MAX_REGULAR_OC.key(), 0);
        if (maxReg > 0) calc.setMaxRegularOverclocks(maxReg);

        final int skips = MachineProfile.getInt(s, Settings.MAX_TIER_SKIPS.key(), 0);
        if (skips > 0) calc.setMaxTierSkips(skips);

        if (MachineProfile.getBool(s, Settings.UNLIMITED_SKIPS.key(), false)) calc.setUnlimitedTierSkips();

        return calc;
    }

    public static long recipeEUt(MachineProfile.RecipeContext ctx) {
        final Long euPerTick = ctx.get(EU_PER_TICK);
        if (euPerTick != null && euPerTick > 0) return euPerTick;
        final Long totalEu = ctx.get(TOTAL_EU);
        if (totalEu != null && totalEu > 0 && ctx.recipeDuration() > 0) return totalEu / ctx.recipeDuration();
        return 0;
    }

}
