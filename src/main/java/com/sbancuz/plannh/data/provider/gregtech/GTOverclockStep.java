package com.sbancuz.plannh.data.provider.gregtech;

import static com.sbancuz.plannh.data.provider.GTProvider.EU_PER_TICK;
import static com.sbancuz.plannh.data.provider.GTProvider.RECIPE_MAP;
import static com.sbancuz.plannh.data.provider.GTProvider.TOTAL_EU;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.EffectComputer;
import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.effect.EffectStep;

import gregtech.api.enums.GTValues;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.OverclockCalculator;

public class GTOverclockStep implements EffectStep, EffectComputer {

    private record Condition(Predicate<RecipeContext> predicate, Consumer<GTOverclockStep> configurator) {}

    private GTOverclockStep() {}

    public static GTOverclockStep create() {
        return new GTOverclockStep();
    }

    private boolean forceHeat;
    private boolean forcePerfectOC;
    private final List<Condition> conditions = new ArrayList<>();
    private final Map<String, Consumer<GTOverclockStep>> routeModifiers = new HashMap<>();
    private SettingDef<Integer> catalystSetting;
    private IntUnaryOperator catalystComputer;

    public GTOverclockStep withCatalyst(final SettingDef<Integer> setting, final IntUnaryOperator computer) {
        this.catalystSetting = setting;
        this.catalystComputer = computer;
        return this;
    }

    public GTOverclockStep route(final String recipeMapId, final Consumer<GTOverclockStep> modifier) {
        routeModifiers.put(recipeMapId, modifier);
        return this;
    }

    public GTOverclockStep withHeat() {
        this.forceHeat = true;
        return this;
    }

    public GTOverclockStep withPerfectOC() {
        this.forcePerfectOC = true;
        return this;
    }

    public GTOverclockStep applyIf(final Predicate<RecipeContext> predicate,
        final Consumer<GTOverclockStep> configurator) {
        conditions.add(new Condition(predicate, configurator));
        return this;
    }

    @Override
    public EffectResult compute(Map<String, Object> s, RecipeContext ctx) {
        final Object dur = ctx.properties().get(RecipePropertyAPI.DURATION_TICKS);
        final int d = dur instanceof final Number n ? n.intValue() : 0;
        return apply(new EffectResult(d, 0, 1), s, ctx);
    }

    @Override
    public EffectResult apply(EffectResult current, Map<String, Object> s, RecipeContext ctx) {
        forceHeat = false;
        forcePerfectOC = false;
        catalystSetting = null;
        catalystComputer = null;

        for (final Condition cond : conditions) {
            if (cond.predicate()
                .test(ctx)) {
                cond.configurator()
                    .accept(this);
            }
        }

        if (!routeModifiers.isEmpty()) {
            final RecipeMap<?> map = ctx.getOrDefault(RECIPE_MAP, null);
            if (map != null) {
                final String uid = map.unlocalizedName;
                final Consumer<GTOverclockStep> mod = routeModifiers.get(uid);
                if (mod != null) mod.accept(this);
            }
        }

        // PARALLELS_DEF's default is 0, meaning "ask the machine", so reading it raw yields a
        // throughput factor of 0 on every path that does not go through the preset - which zeroes
        // every port on the node and makes it silently produce and consume nothing.
        final int settingParallels = Math.max(1, GTSettings.PARALLELS_DEF.effectiveInt(ctx, s));
        final int parallels;
        if (catalystSetting != null) {
            final int cat = MachineProfile.getInt(s, catalystSetting.key, 0);
            parallels = cat > 0 ? catalystComputer.applyAsInt(cat) : settingParallels;
        } else {
            parallels = settingParallels;
        }
        final int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);

        final long eut = recipeEUt(ctx, current);
        final int recipeDuration = current.durationTicks();

        // The recipe as written, which is the answer on every path that does not overclock. Set once
        // here so each of those paths is a bare return rather than a copy of these three lines.
        current.durationTicks(recipeDuration);
        current.energyPerT(eut);
        current.throughputFactor(parallels * machines);

        if (eut <= 0 || recipeDuration <= 0) return current;

        if (MachineProfile.getString(s, Settings.VOLTAGE.key(), "OFF")
            .equals("OFF")) return current;

        final OverclockCalculator calc = buildGtCalc(s, eut, recipeDuration, parallels);

        if (forcePerfectOC) calc.enablePerfectOC();

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
        current.durationTicks(calc.getDuration());
        current.energyPerT(calc.getConsumption());
        return current;
    }

    /**
     * Settings.VOLTAGE's option list is GTValues.VN, so the tier table is GT's to define. The
     * closed form 8*4^tier that this used to compute is wrong at the top end: V[14] is
     * Integer.MAX_VALUE - 7, not 2147483648.
     */
    public static long tierNameToVoltage(@Nullable final String name) {
        if (name == null || name.equals("OFF")) return 0;
        for (int tier = 0; tier < GTValues.VN.length; tier++) {
            if (GTValues.VN[tier].equals(name)) return GTValues.V[tier];
        }
        return 0;
    }

    private static OverclockCalculator buildGtCalc(Map<String, Object> s, long eut, int duration, int parallels) {
        final long voltage = tierNameToVoltage(MachineProfile.getString(s, Settings.VOLTAGE.key(), "OFF"));
        final long amp = MachineProfile.getInt(s, Settings.AMP.key(), 1);
        final int speed = MachineProfile.getInt(s, Settings.SPEED.key(), 100);

        final OverclockCalculator calc = new OverclockCalculator().setRecipeEUt(eut)
            .setEUt(voltage)
            .setDuration(duration)
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

    static long recipeEUt(final RecipeContext ctx, final EffectResult current) {
        final long fromRecipe = recipeEUt(ctx, current.durationTicks());
        return fromRecipe > 0 ? fromRecipe : current.energyPerT();
    }

    /**
     * The recipe's own EU/t, for callers that have no {@link EffectResult} to fall back on - the
     * settings rows, which need it to know which voltage tiers can run the recipe at all.
     */
    public static long recipeEUt(final RecipeContext ctx) {
        return recipeEUt(ctx, ctx.getOrDefault(RecipePropertyAPI.DURATION_TICKS, 0));
    }

    private static long recipeEUt(final RecipeContext ctx, final int duration) {
        final Long euPerTick = ctx.getOrDefault(EU_PER_TICK, null);
        if (euPerTick != null && euPerTick > 0) return euPerTick;
        final Long totalEu = ctx.getOrDefault(TOTAL_EU, null);
        if (totalEu != null && totalEu > 0 && duration > 0) return totalEu / duration;
        return 0;
    }
}
