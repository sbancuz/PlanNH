package com.sbancuz.plannh.data.setting;

import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.data.MachineConfig;

public enum Settings {

    // ── int settings ──
    AMP("amp", 1, 1, 64, (v, _) -> "A" + v),
    SPEED("speed", 100, 10, 10000, (v, _) -> "⏱" + v + "%"),
    TICK_MODIFIER("tick_modifier", 100, 10, 10000, (v, _) -> "⏩" + v + "%"),
    PARALLELS("parallels", 1, 1, 4096, (v, _) -> "∥" + v),
    MACHINES("machines", 1, 1, 4096, (v, _) -> "×" + v),

    MACHINE_HEAT("machine_heat", 0, 0, 100000, (v, _) -> "M" + v),
    RECIPE_HEAT("recipe_heat", 0, 0, 100000, (v, _) -> "R" + v),
    HEAT_DISCOUNT_MULT("heat_discount_mult", 100, 0, 200),

    EUT_DISCOUNT("eut_discount", 0, 0, 100, (v, _) -> "D" + v + "%"),
    EUT_INCREASE_PER_OC("eut_increase_per_oc", 400, 100, 1000, (v, _) -> "EU×" + v / 100),
    DURATION_DECREASE_PER_OC("duration_decrease_per_oc", 200, 100, 1000, (v, _) -> "Spd×" + v / 100),
    MAX_OVERCLOCKS("max_overclocks", 0, 0, 64, (v, _) -> "OC" + v),
    MAX_REGULAR_OC("max_regular_oc", 0, 0, 64, (v, _) -> "Rg" + v),
    MAX_TIER_SKIPS("max_tier_skips", 0, 0, 10, (v, _) -> "Sk" + v),

    FUEL_EFFICIENCY("fuel_efficiency", 100, 1, 1000),

    ENERGY_PER_TICK("energy_per_tick", 10, 1, 10000),
    MANA_PER_TICK("mana_per_tick", 10, 1, 10000),
    VIS_PER_TICK("vis_per_tick", 1, 1, 100),
    RF_PER_TICK("rf_per_tick", 80, 1, 10000),
    FORESTRY_RF_PER_TICK("forestry_rf_per_tick", 10, 1, 10000),

    // ── bool settings ──
    PERFECT_OC("perfect_oc", false, (v, _) -> v ? "P" : null),
    HEAT_OC("heat_oc", true, (v, _) -> v ? "H" : null),
    HEAT_DISCOUNT("heat_discount", false, (v, _) -> v ? "D" : null),
    LASER_OC("laser_oc", false, (v, _) -> v ? "L" : null),
    UNLIMITED_SKIPS("unlimited_skips", false, (v, _) -> v ? "∞T" : null),
    NO_OVERCLOCK("no_overclock", false, (v, _) -> v ? "NO" : null),

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
        this.def = new IntegerSettingDef(key, defaultValue, min, max);
    }

    Settings(final String key, final int defaultValue, final int min, final int max,
        final BiFunction<Integer, MachineConfig, String> badgeFn) {
        this.def = new IntegerSettingDef(key, defaultValue, min, max, badgeFn);
    }

    Settings(final String key, final boolean defaultValue, final BiFunction<Boolean, MachineConfig, String> badgeFn) {
        this.def = new BooleanSettingDef(key, defaultValue, badgeFn);
    }

    Settings(final String key, final String defaultValue, final List<String> options,
        final BiFunction<String, MachineConfig, String> badgeFn) {
        this.def = new StringSettingDef(key, defaultValue, options, badgeFn);
    }

    @Nonnull
    public SettingDef<?> def() {
        return def;
    }

    @Nonnull
    public String key() {
        return def.getKey();
    }
}
