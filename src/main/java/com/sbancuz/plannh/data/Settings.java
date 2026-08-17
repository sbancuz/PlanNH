package com.sbancuz.plannh.data;

import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;

// TODO: Rework these making them customizable
public enum Settings {

    // ── int settings ──
    DURATION_TICKS("duration_ticks", 0, 0, 1000000, (v, c) -> v > 0 ? v + "t" : null),
    AMP("amp", 1, 1, 64, (v, c) -> "A" + v),
    SPEED("speed", 100, 10, 10000, (v, c) -> "\u23F1" + v + "%"),
    TICK_MODIFIER("tick_modifier", 100, 10, 10000, (v, c) -> "\u23E9" + v + "%"),
    PARALLELS("parallels", 1, 1, 4096, (v, c) -> "\u2225" + v),
    MACHINES("machines", 1, 1, 4096, (v, c) -> "\u00D7" + v),

    MACHINE_HEAT("machine_heat", 0, 0, 100000, (v, c) -> "M" + v),
    RECIPE_HEAT("recipe_heat", 0, 0, 100000, (v, c) -> "R" + v),
    HEAT_DISCOUNT_MULT("heat_discount_mult", 100, 0, 200),

    EUT_DISCOUNT("eut_discount", 0, 0, 100, (v, c) -> "D" + v + "%"),
    EUT_INCREASE_PER_OC("eut_increase_per_oc", 400, 100, 1000, (v, c) -> "EU\u00D7" + (v / 100)),
    DURATION_DECREASE_PER_OC("duration_decrease_per_oc", 200, 100, 1000, (v, c) -> "Spd\u00D7" + (v / 100)),
    MAX_OVERCLOCKS("max_overclocks", 0, 0, 64, (v, c) -> "OC" + v),
    MAX_REGULAR_OC("max_regular_oc", 0, 0, 64, (v, c) -> "Rg" + v),
    MAX_TIER_SKIPS("max_tier_skips", 0, 0, 10, (v, c) -> "Sk" + v),

    FUEL_EFFICIENCY("fuel_efficiency", 100, 1, 1000),

    ENERGY_PER_TICK("energy_per_tick", 10, 1, 10000),
    MANA_PER_TICK("mana_per_tick", 10, 1, 10000),
    VIS_PER_TICK("vis_per_tick", 1, 1, 100),
    RF_PER_TICK("rf_per_tick", 80, 1, 10000),
    INPUTS_PER_TICK("inputs_per_tick", 1, 1, 10000),
    LP_PER_TICK("lp_per_tick", 20, 1, 100000),

    STEAM_EUT_DISCOUNT("steam_eut_discount", 100, 1, 10000, (v, c) -> "\u2622" + v + "%"),
    STEAM_DURATION_MODIFIER("steam_duration_modifier", 100, 1, 10000, (v, c) -> "\u23F1" + v + "%"),

    // ── bool settings ──
    PERFECT_OC("perfect_oc", false, (v, c) -> v ? "P" : null),
    HEAT_OC("heat_oc", true, (v, c) -> v ? "H" : null),
    HEAT_DISCOUNT("heat_discount", false, (v, c) -> v ? "D" : null),
    LASER_OC("laser_oc", false, (v, c) -> v ? "L" : null),
    UNLIMITED_SKIPS("unlimited_skips", false, (v, c) -> v ? "\u221ET" : null),
    NO_OVERCLOCK("no_overclock", false, (v, c) -> v ? "NO" : null),
    GT_MULTIBLOCK("gt_multiblock", false, (v, c) -> v ? "M" : null),
    CATALYST_ASTRAL_ARRAYS("catalyst_astral_arrays", 0, 0, 8637, (v, c) -> v > 0 ? "\u2606" + v : null),
    CATALYST_ACCEL_CARD("catalyst_accel_card", 0, 0, 5, (v, c) -> v > 0 ? "\u2606" + v : null),

    // ── settings the mod that owns the machine defines ──
    // Only the key is declared. The provider attaches the def, so no mod's voltage names, coil names
    // or tier ceilings are written down here; see GTSettings and GTProvider.machineDriven. The GT_
    // prefix follows GT_MULTIBLOCK above, and is honest: a coil is a GregTech idea, and the next mod's
    // tier knob will not be a coil. Structure knobs are declared in the order a machine table lists
    // them, because an EnumSet of them iterates in declaration order.
    VOLTAGE("voltage"),
    GT_COIL("gt_coil"),
    GT_SOLENOID("gt_solenoid"),
    GT_ITEM_PIPE("gt_item_pipe"),
    GT_PIPE_CASING("gt_pipe_casing"),
    GT_SAWBLADE("gt_sawblade"),
    GT_ELECTRODE("gt_electrode"),
    GT_STRUCTURE_TIER("gt_structure_tier"),
    GT_WIDTH("gt_width"),
    GT_MODE("gt_mode"),

    // ── enum-type settings ──
    BURNABLE_OVERRIDE("burnable_override", "OFF", List.of("OFF", "IN", "OUT"), (_, _) -> null),

    //
    ;

    private final SettingDef<?> def;

    /** A setting the owning mod defines; see {@link SettingDef#providedDef}. */
    Settings(final String key) {
        this.def = SettingDef.providedDef(key);
    }

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
