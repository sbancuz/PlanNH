package com.sbancuz.plannh.data.setting;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.sbancuz.plannh.PlanNH;

public final class Settings {

    private Settings() {}

    private static final Map<String, SettingDef<?>> ALL_SETTINGS = new HashMap<>();

    public static void register(SettingDef<?> def) {
        if (ALL_SETTINGS.containsKey(def.key)) {
            PlanNH.LOG.error("Duplicate key " + def.key + " registered, overriding...");
        }
        ALL_SETTINGS.put(def.key, def);
    }

    public static @Nullable SettingDef<?> get(String key) {
        return ALL_SETTINGS.get(key);
    }

    // ── int settings ──
    public static final IntegerSettingDef DURATION_TICKS = new IntegerSettingDef("duration_ticks", 0, 0, 1000000);
    public static final IntegerSettingDef AMP = new IntegerSettingDef("amp", 1, 1, 64);
    public static final IntegerSettingDef SPEED = new IntegerSettingDef("speed", 100, 10, 10000);
    public static final IntegerSettingDef TICK_MODIFIER = new IntegerSettingDef("tick_modifier", 100, 10, 10000);
    public static final IntegerSettingDef PARALLELS = new IntegerSettingDef("parallels", 1, 1, 4096);
    public static final IntegerSettingDef MACHINES = new IntegerSettingDef("machines", 1, 1, 4096);

    public static final IntegerSettingDef MACHINE_HEAT = new IntegerSettingDef("machine_heat", 0, 0, 100000);
    public static final IntegerSettingDef RECIPE_HEAT = new IntegerSettingDef("recipe_heat", 0, 0, 100000);
    public static final IntegerSettingDef HEAT_DISCOUNT_MULT = new IntegerSettingDef("heat_discount_mult", 100, 0, 200);

    public static final IntegerSettingDef EUT_DISCOUNT = new IntegerSettingDef("eut_discount", 0, 0, 100);
    public static final IntegerSettingDef EUT_INCREASE_PER_OC = new IntegerSettingDef(
        "eut_increase_per_oc",
        400,
        100,
        1000);
    public static final IntegerSettingDef DURATION_DECREASE_PER_OC = new IntegerSettingDef(
        "duration_decrease_per_oc",
        200,
        100,
        1000);
    public static final IntegerSettingDef MAX_OVERCLOCKS = new IntegerSettingDef("max_overclocks", 0, 0, 64);
    public static final IntegerSettingDef MAX_REGULAR_OC = new IntegerSettingDef("max_regular_oc", 0, 0, 64);
    public static final IntegerSettingDef MAX_TIER_SKIPS = new IntegerSettingDef("max_tier_skips", 0, 0, 10);

    public static final IntegerSettingDef MANA_PER_TICK = new IntegerSettingDef("mana_per_tick", 10, 1, 10000);
    public static final IntegerSettingDef VIS_PER_TICK = new IntegerSettingDef("vis_per_tick", 1, 1, 100);
    public static final IntegerSettingDef INPUTS_PER_TICK = new IntegerSettingDef("inputs_per_tick", 1, 1, 10000);
    public static final IntegerSettingDef LP_PER_TICK = new IntegerSettingDef("lp_per_tick", 20, 1, 100000);

    public static final IntegerSettingDef STEAM_EUT_DISCOUNT = new IntegerSettingDef(
        "steam_eut_discount",
        100,
        1,
        10000);
    public static final IntegerSettingDef STEAM_DURATION_MODIFIER = new IntegerSettingDef(
        "steam_duration_modifier",
        100,
        1,
        10000);

    // ── bool settings ──
    public static final BooleanSettingDef PERFECT_OC = new BooleanSettingDef("perfect_oc", false);
    public static final BooleanSettingDef HEAT_OC = new BooleanSettingDef("heat_oc", true);
    public static final BooleanSettingDef HEAT_DISCOUNT = new BooleanSettingDef("heat_discount", false);
    public static final BooleanSettingDef LASER_OC = new BooleanSettingDef("laser_oc", false);
    public static final BooleanSettingDef UNLIMITED_SKIPS = new BooleanSettingDef("unlimited_skips", false);
    public static final BooleanSettingDef NO_OVERCLOCK = new BooleanSettingDef("no_overclock", false);
    public static final BooleanSettingDef GT_MULTIBLOCK = new BooleanSettingDef("gt_multiblock", false);
    public static final IntegerSettingDef CATALYST_ASTRAL_ARRAYS = new IntegerSettingDef(
        "catalyst_astral_arrays",
        0,
        0,
        8637);
    public static final IntegerSettingDef CATALYST_ACCEL_CARD = new IntegerSettingDef("catalyst_accel_card", 0, 0, 5);

    // ── enum-type settings ──
    public static final EnumSettingDef<Voltage> VOLTAGE = new EnumSettingDef<>("voltage", Voltage.OFF, Voltage.class);
    public static final EnumSettingDef<Burnable> BURNABLE_OVERRIDE = new EnumSettingDef<>(
        "burnable_override",
        Burnable.OFF,
        Burnable.class);

    public enum Voltage {
        OFF,
        ULV,
        LV,
        MV,
        HV,
        EV,
        IV,
        LuV,
        ZPM,
        UV,
        UHV,
        UEV,
        UIV,
        UMV,
        UXV,
        MAX;
    }

    public enum Burnable {
        OFF,
        IN,
        OUT;
    }
}
