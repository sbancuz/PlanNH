package com.sbancuz.plannh.data;

import java.util.List;

public enum Settings {

    // ── enum-type settings ──
    VOLTAGE("voltage", "Tier", "OFF", List
        .of("OFF", "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV", "MAX")),

    // ── int settings ──
    AMP("amp", "Amp", 1, 1, 64),
    SPEED("speed", "Speed", 100, 10, 10000),
    PARALLELS("parallels", "Par", 1, 1, 4096),
    MACHINES("machines", "Mach", 1, 1, 4096),

    MACHINE_HEAT("machineHeat", "M. Heat", 0, 0, 100000),
    RECIPE_HEAT("recipeHeat", "R. Heat", 0, 0, 100000),
    HEAT_DISCOUNT_MULT("heatDiscountMult", "HD Mult.%", 100, 0, 200),

    EUT_DISCOUNT("eutDiscount", "EU Disc.%", 0, 0, 100),
    EUT_INCREASE_PER_OC("eutIncreasePerOC", "EU%/OC", 400, 100, 1000),
    DURATION_DECREASE_PER_OC("durationDecreasePerOC", "Spd%/OC", 200, 100, 1000),
    MAX_OVERCLOCKS("maxOverclocks", "Max OC", 0, 0, 64),
    MAX_REGULAR_OC("maxRegularOc", "Max Reg OC", 0, 0, 64),
    MAX_TIER_SKIPS("maxTierSkips", "Max Skips", 0, 0, 10),

    FUEL_EFFICIENCY("fuelEfficiency", "Fuel Eff.%", 100, 1, 1000),

    ENERGY_PER_TICK("energyPerTick", "AE/t", 10, 1, 10000),
    MANA_PER_TICK("manaPerTick", "Mana/t", 10, 1, 10000),
    VIS_PER_TICK("visPerTick", "Vis/t", 1, 1, 100),
    RF_PER_TICK("rfPerTick", "RF/t", 80, 1, 10000),
    FORESTRY_RF_PER_TICK("forestryRfPerTick", "RF/t", 10, 1, 10000),

    // ── bool settings ──
    PERFECT_OC("perfectOC", "Perfect OC", false),
    HEAT_OC("heatOC", "Heat OC", true),
    HEAT_DISCOUNT("heatDiscount", "Heat Disc.", false),
    LASER_OC("laserOC", "Laser OC", false),
    UNLIMITED_SKIPS("unlimitedSkips", "Unl. Skips", false),
    NO_OVERCLOCK("noOverclock", "No OC", false);

    private final SettingDef<?> def;

    Settings(String key, String label, int defaultValue, int min, int max) {
        this.def = SettingDef.intDef(key, label, defaultValue, min, max);
    }

    Settings(String key, String label, boolean defaultValue) {
        this.def = SettingDef.boolDef(key, label, defaultValue);
    }

    Settings(String key, String label, String defaultValue, List<String> options) {
        this.def = SettingDef.enumDef(key, label, defaultValue, options);
    }

    public SettingDef<?> def() {
        return def;
    }

    public String key() {
        return def.key;
    }
}
