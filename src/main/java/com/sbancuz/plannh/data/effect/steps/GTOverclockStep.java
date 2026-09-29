package com.sbancuz.plannh.data.effect.steps;

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
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.effect.EffectComputer;
import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.effect.EffectStep;
import com.sbancuz.plannh.data.setting.IntegerSettingDef;
import com.sbancuz.plannh.data.setting.SettingDef;
import com.sbancuz.plannh.data.setting.Settings;

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
    private final Map<String, Map<SettingDef<?>, Object>> routeDefaults = new HashMap<>();
    private IntegerSettingDef catalystSetting;
    private IntUnaryOperator catalystComputer;

    public GTOverclockStep withCatalyst(final IntegerSettingDef setting, final IntUnaryOperator computer) {
        this.catalystSetting = setting;
        this.catalystComputer = computer;
        return this;
    }

    public GTOverclockStep route(final String recipeMapId, final Consumer<GTOverclockStep> modifier) {
        routeModifiers.put(recipeMapId, modifier);
        return this;
    }

    public GTOverclockStep withDefault(final String recipeMapId, final SettingDef<?> key, final Object value) {
        routeDefaults.computeIfAbsent(recipeMapId, k -> new HashMap<>())
            .put(key, value);
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
    public EffectResult compute(MachineConfig config, RecipeContext ctx) {
        final Object dur = ctx.properties().get(RecipePropertyAPI.DURATION_TICKS);
        final int d = dur instanceof final Number n ? n.intValue() : 0;
        return apply(new EffectResult(d, 0, 1), config, ctx);
    }

    @Override
    public Map<SettingDef<?>, Object> routeDefaults(final RecipeContext ctx) {
        final RecipeMap<?> map = ctx.getOrDefault(RECIPE_MAP, null);
        if (map == null || routeDefaults.isEmpty()) return Map.of();
        return routeDefaults.getOrDefault(map.unlocalizedName, Map.of());
    }

    @Override
    public EffectResult apply(EffectResult current, MachineConfig config, RecipeContext ctx) {
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

        if (!routeModifiers.isEmpty() || !routeDefaults.isEmpty()) {
            final RecipeMap<?> map = ctx.getOrDefault(RECIPE_MAP, null);
            if (map != null) {
                final String uid = map.unlocalizedName;
                final Consumer<GTOverclockStep> mod = routeModifiers.get(uid);
                if (mod != null) mod.accept(this);
                final Map<SettingDef<?>, Object> defs = routeDefaults.get(uid);
                if (defs != null) config.applyDefaults(defs);
            }
        }

        final int parallels;
        if (catalystSetting != null) {
            final int cat = config.get(catalystSetting);
            parallels = cat > 0 ? catalystComputer.applyAsInt(cat) : config.get(Settings.PARALLELS);
        } else {
            parallels = config.get(Settings.PARALLELS);
        }
        final int machines = config.get(Settings.MACHINES);

        final long eut = recipeEUt(ctx, current);
        final int recipeDuration = current.durationTicks();

        if (eut <= 0 || recipeDuration <= 0 || config.get(Settings.VOLTAGE) == Settings.Voltage.OFF) {
            current.durationTicks(recipeDuration);
            current.energyPerT(eut);
            current.throughputFactor(parallels * machines);
            return current;
        }

        final OverclockCalculator calc = buildGtCalc(config, eut, recipeDuration, parallels);

        if (forcePerfectOC) calc.enablePerfectOC();

        if (forceHeat) {
            final int machineHeat = config.get(Settings.MACHINE_HEAT);
            if (config.get(Settings.HEAT_OC) && machineHeat > 0) {
                final int recipeHeat = config.get(Settings.RECIPE_HEAT);
                calc.setHeatOC(true)
                    .setRecipeHeat(recipeHeat > 0 ? recipeHeat : machineHeat)
                    .setMachineHeat(machineHeat);
                if (config.get(Settings.HEAT_DISCOUNT)) calc.setHeatDiscount(true);
                final int hdMult = config.get(Settings.HEAT_DISCOUNT_MULT);
                if (hdMult != 100) calc.setHeatDiscountMultiplier(hdMult / 100.0);
            }
        }

        calc.calculate();
        current.durationTicks(calc.getDuration());
        current.energyPerT(calc.getConsumption());
        current.throughputFactor(parallels * machines);
        return current;
    }

    public static long tierNameToVoltage(@Nullable final String name) {
        if (name == null || name.equals("OFF")) return 0;
        final String[] names = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV",
            "UXV", "MAX" };
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) return 8L * (long) Math.pow(4, i);
        }
        return 0;
    }

    private static OverclockCalculator buildGtCalc(MachineConfig config, long eut, int duration, int parallels) {
        final long voltage = tierNameToVoltage(
            config.get(Settings.VOLTAGE)
                .name());
        final int amp = config.get(Settings.AMP);
        final int speed = config.get(Settings.SPEED);

        final OverclockCalculator calc = new OverclockCalculator().setRecipeEUt(eut)
            .setEUt(voltage)
            .setDuration(duration)
            .setAmperage(amp)
            .setDurationModifier(100.0 / speed)
            .setParallel(parallels)
            .setAmperageOC(true);

        if (config.get(Settings.PERFECT_OC)) calc.enablePerfectOC();
        if (config.get(Settings.LASER_OC)) calc.setLaserOC(true);
        if (config.get(Settings.NO_OVERCLOCK)) calc.setNoOverclock(true);

        final int eutDisc = config.get(Settings.EUT_DISCOUNT);
        if (eutDisc > 0) calc.setEUtDiscount(eutDisc / 100.0);

        final int ocMult = config.get(Settings.EUT_INCREASE_PER_OC);
        if (ocMult != 400) calc.setEUtIncreasePerOC(ocMult / 100.0);

        final int durMult = config.get(Settings.DURATION_DECREASE_PER_OC);
        if (durMult != 200) calc.setDurationDecreasePerOC(durMult / 100.0);

        final int maxOc = config.get(Settings.MAX_OVERCLOCKS);
        if (maxOc > 0) calc.setMaxOverclocks(maxOc);

        final int maxReg = config.get(Settings.MAX_REGULAR_OC);
        if (maxReg > 0) calc.setMaxRegularOverclocks(maxReg);

        final int skips = config.get(Settings.MAX_TIER_SKIPS);
        if (skips > 0) calc.setMaxTierSkips(skips);

        if (config.get(Settings.UNLIMITED_SKIPS)) calc.setUnlimitedTierSkips();

        return calc;
    }

    static long recipeEUt(RecipeContext ctx, EffectResult current) {
        final Long euPerTick = ctx.getOrDefault(EU_PER_TICK, null);
        if (euPerTick != null && euPerTick > 0) return euPerTick;
        final Long totalEu = ctx.getOrDefault(TOTAL_EU, null);
        if (totalEu != null && totalEu > 0 && current.durationTicks() > 0) return totalEu / current.durationTicks();
        return current.energyPerT();
    }
}
