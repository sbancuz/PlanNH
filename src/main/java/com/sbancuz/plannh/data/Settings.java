package com.sbancuz.plannh.data;

import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;

public enum Settings {

    // ── int settings ──
    AMP("amp", 1, 1, 64, (v, c) -> "A" + v),
    SPEED("speed", 100, 10, 10000, (v, c) -> "\u23F1" + v + "%"),
    TICK_MODIFIER("tickModifier", 100, 10, 10000, (v, c) -> "\u23E9" + v + "%"),
    PARALLELS("parallels", 1, 1, 4096, (v, c) -> "\u2225" + v),
    MACHINES("machines", 1, 1, 4096, (v, c) -> "\u00D7" + v),

    MACHINE_HEAT("machineHeat", 0, 0, 100000, (v, c) -> "M" + v),
    RECIPE_HEAT("recipeHeat", 0, 0, 100000, (v, c) -> "R" + v),
    HEAT_DISCOUNT_MULT("heatDiscountMult", 100, 0, 200),

    EUT_DISCOUNT("eutDiscount", 0, 0, 100, (v, c) -> "D" + v + "%"),
    EUT_INCREASE_PER_OC("eutIncreasePerOC", 400, 100, 1000, (v, c) -> "EU\u00D7" + (v / 100)),
    DURATION_DECREASE_PER_OC("durationDecreasePerOC", 200, 100, 1000, (v, c) -> "Spd\u00D7" + (v / 100)),
    MAX_OVERCLOCKS("maxOverclocks", 0, 0, 64, (v, c) -> "OC" + v),
    MAX_REGULAR_OC("maxRegularOc", 0, 0, 64, (v, c) -> "Rg" + v),
    MAX_TIER_SKIPS("maxTierSkips", 0, 0, 10, (v, c) -> "Sk" + v),

    FUEL_EFFICIENCY("fuelEfficiency", 100, 1, 1000),

    ENERGY_PER_TICK("energyPerTick", 10, 1, 10000),
    MANA_PER_TICK("manaPerTick", 10, 1, 10000),
    VIS_PER_TICK("visPerTick", 1, 1, 100),
    RF_PER_TICK("rfPerTick", 80, 1, 10000),
    FORESTRY_RF_PER_TICK("forestryRfPerTick", 10, 1, 10000),

    // ── bool settings ──
    PERFECT_OC("perfectOC", false, (v, c) -> v ? "P" : null),
    HEAT_OC("heatOC", true, (v, c) -> v ? "H" : null),
    HEAT_DISCOUNT("heatDiscount", false, (v, c) -> v ? "D" : null),
    LASER_OC("laserOC", false, (v, c) -> v ? "L" : null),
    UNLIMITED_SKIPS("unlimitedSkips", false, (v, c) -> v ? "\u221ET" : null),
    NO_OVERCLOCK("noOverclock", false, (v, c) -> v ? "NO" : null),

    // ── enum-type settings ──
    VOLTAGE("voltage", "OFF", List
        .of("OFF", "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV", "MAX"),
        (v, c) -> {
            if ("OFF".equals(v)) return null;
            return c.getBoolean(PERFECT_OC.key()) ? v + "P" : v;
        }),
    BURNABLE_OVERRIDE("burnable_override", "OFF", List.of("OFF", "IN", "OUT"), (_, _) -> null),

    //
    ;

    private final SettingDef<?> def;

    Settings(final String key, final int defaultValue, final int min, final int max) {
        this.def = SettingDef.intDef(key, defaultValue, min, max);
    }

    Settings(final String key, final int defaultValue, final int min, final int max,
        final BiFunction<Integer, MachineConfig, String> badgeFn) {
        this.def = SettingDef.intDef(key, defaultValue, min, max, badgeFn);
    }

    Settings(final String key, final boolean defaultValue, final BiFunction<Boolean, MachineConfig, String> badgeFn) {
        this.def = SettingDef.boolDef(key, defaultValue, badgeFn);
    }

    Settings(final String key, final String defaultValue, final List<String> options,
        final BiFunction<String, MachineConfig, String> badgeFn) {
        this.def = SettingDef.enumDef(key, defaultValue, options, badgeFn);
    }

    @Nonnull
    public SettingDef<?> def() {
        return def;
    }

    @Nonnull
    public String key() {
        return def.key;
    }
}
