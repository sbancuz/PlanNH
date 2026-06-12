package com.sbancuz.plannh.data;

import java.util.List;
import java.util.function.BiFunction;

public enum Settings {

    // ── enum-type settings ──
    VOLTAGE("voltage", "Tier", "OFF", List
        .of("OFF", "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV", "MAX"),
        (v, c) -> {
            if ("OFF".equals(v)) return null;
            return c.getBoolean("perfectOC") ? v + "P" : v;
        }),

    // ── int settings ──
    AMP("amp", "Amp", 1, 1, 64, (v, c) -> "A" + v),
    SPEED("speed", "Speed", 100, 10, 10000, (v, c) -> "\u23F1" + v + "%"),
    TICK_MODIFIER("tickModifier", "Accel", 100, 10, 10000, (v, c) -> "\u23E9" + v + "%"),
    PARALLELS("parallels", "Par", 1, 1, 4096, (v, c) -> "\u2225" + v),
    MACHINES("machines", "Mach", 1, 1, 4096, (v, c) -> "\u00D7" + v),

    MACHINE_HEAT("machineHeat", "M. Heat", 0, 0, 100000, (v, c) -> "M" + v),
    RECIPE_HEAT("recipeHeat", "R. Heat", 0, 0, 100000, (v, c) -> "R" + v),
    HEAT_DISCOUNT_MULT("heatDiscountMult", "HD Mult.%", 100, 0, 200),

    EUT_DISCOUNT("eutDiscount", "EU Disc.%", 0, 0, 100, (v, c) -> "D" + v + "%"),
    EUT_INCREASE_PER_OC("eutIncreasePerOC", "EU%/OC", 400, 100, 1000, (v, c) -> "EU\u00D7" + (v / 100)),
    DURATION_DECREASE_PER_OC("durationDecreasePerOC", "Spd%/OC", 200, 100, 1000, (v, c) -> "Spd\u00D7" + (v / 100)),
    MAX_OVERCLOCKS("maxOverclocks", "Max OC", 0, 0, 64, (v, c) -> "OC" + v),
    MAX_REGULAR_OC("maxRegularOc", "Max Reg OC", 0, 0, 64, (v, c) -> "Rg" + v),
    MAX_TIER_SKIPS("maxTierSkips", "Max Skips", 0, 0, 10, (v, c) -> "Sk" + v),

    FUEL_EFFICIENCY("fuelEfficiency", "Fuel Eff.%", 100, 1, 1000),

    ENERGY_PER_TICK("energyPerTick", "AE/t", 10, 1, 10000),
    MANA_PER_TICK("manaPerTick", "Mana/t", 10, 1, 10000),
    VIS_PER_TICK("visPerTick", "Vis/t", 1, 1, 100),
    RF_PER_TICK("rfPerTick", "RF/t", 80, 1, 10000),
    FORESTRY_RF_PER_TICK("forestryRfPerTick", "RF/t", 10, 1, 10000),

    // ── bool settings ──
    PERFECT_OC("perfectOC", "Perfect OC", false, (v, c) -> v ? "P" : null),
    HEAT_OC("heatOC", "Heat OC", true, (v, c) -> v ? "H" : null),
    HEAT_DISCOUNT("heatDiscount", "Heat Disc.", false, (v, c) -> v ? "D" : null),
    LASER_OC("laserOC", "Laser OC", false, (v, c) -> v ? "L" : null),
    UNLIMITED_SKIPS("unlimitedSkips", "Unl. Skips", false, (v, c) -> v ? "\u221ET" : null),
    NO_OVERCLOCK("noOverclock", "No OC", false, (v, c) -> v ? "NO" : null);

    private final SettingDef<?> def;

    Settings(String key, String label, int defaultValue, int min, int max) {
        this.def = SettingDef.intDef(key, label, defaultValue, min, max);
    }

    Settings(String key, String label, int defaultValue, int min, int max,
        BiFunction<Integer, MachineConfig, String> badgeFn) {
        this.def = SettingDef.intDef(key, label, defaultValue, min, max, badgeFn);
    }

    Settings(String key, String label, boolean defaultValue, BiFunction<Boolean, MachineConfig, String> badgeFn) {
        this.def = SettingDef.boolDef(key, label, defaultValue, badgeFn);
    }

    Settings(String key, String label, String defaultValue, List<String> options,
        BiFunction<String, MachineConfig, String> badgeFn) {
        this.def = SettingDef.enumDef(key, label, defaultValue, options, badgeFn);
    }

    public SettingDef<?> def() {
        return def;
    }

    public String key() {
        return def.key;
    }
}
