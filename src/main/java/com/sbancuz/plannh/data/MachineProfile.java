package com.sbancuz.plannh.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sbancuz.plannh.api.RecipePropertyAPI;

public record MachineProfile(String id, String displayName, List<SettingDef<?>> settings,
    EffectComputer effectComputer) {

    @FunctionalInterface
    public interface EffectComputer {

        EffectResult compute(Map<String, Object> settings, RecipeContext ctx);
    }

    public record EffectResult(int durationTicks, long energyPerT, int throughputFactor) {}

    public record RecipeContext(Map<RecipeProperty<?>, Object> properties, int recipeDuration) {

        public long recipeEUt() {
            Long euPerTick = get(RecipePropertyAPI.EU_PER_TICK);
            if (euPerTick != null && euPerTick > 0) return euPerTick;
            Long totalEu = get(RecipePropertyAPI.TOTAL_EU);
            if (totalEu != null && totalEu > 0 && recipeDuration > 0) return totalEu / recipeDuration;
            return 0;
        }

        @SuppressWarnings("unchecked")
        public <T> T get(RecipeProperty<T> prop) {
            return (T) properties.get(prop);
        }
    }

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    public static class Builder {

        private final String id;
        private final String displayName;
        private final List<SettingDef<?>> settings = new ArrayList<>();
        private EffectComputer effectComputer;

        private Builder(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public Builder addSetting(SettingDef<?> setting) {
            settings.add(setting);
            return this;
        }

        public Builder baseSettings() {
            settings.add(Settings.VOLTAGE.def());
            settings.add(Settings.AMP.def());
            settings.add(Settings.SPEED.def());
            settings.add(Settings.PARALLELS.def());
            settings.add(Settings.MACHINES.def());
            settings.add(Settings.PERFECT_OC.def());
            return this;
        }

        public Builder heatSettings() {
            settings.add(Settings.MACHINE_HEAT.def());
            settings.add(Settings.RECIPE_HEAT.def());
            settings.add(Settings.HEAT_OC.def());
            settings.add(Settings.HEAT_DISCOUNT.def());
            settings.add(Settings.HEAT_DISCOUNT_MULT.def());
            return this;
        }

        public Builder advancedSettings() {
            settings.add(Settings.LASER_OC.def());
            settings.add(Settings.EUT_DISCOUNT.def());
            settings.add(Settings.EUT_INCREASE_PER_OC.def());
            settings.add(Settings.DURATION_DECREASE_PER_OC.def());
            settings.add(Settings.MAX_OVERCLOCKS.def());
            settings.add(Settings.MAX_REGULAR_OC.def());
            settings.add(Settings.MAX_TIER_SKIPS.def());
            settings.add(Settings.UNLIMITED_SKIPS.def());
            settings.add(Settings.NO_OVERCLOCK.def());
            return this;
        }

        public Builder setting(SettingDef<?> s) {
            return addSetting(s);
        }

        public Builder effect(EffectComputer effect) {
            this.effectComputer = effect;
            return this;
        }

        public MachineProfile build() {
            return new MachineProfile(id, displayName, List.copyOf(settings), effectComputer);
        }
    }

    public static int getInt(Map<String, Object> s, String key, int def) {
        Object v = s.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    public static boolean getBool(Map<String, Object> s, String key, boolean def) {
        Object v = s.get(key);
        return v instanceof Boolean b ? b : def;
    }

    public static String getString(Map<String, Object> s, String key, String def) {
        Object v = s.get(key);
        return v instanceof String str ? str : def;
    }

    public static long tierNameToVoltage(String name) {
        if (name == null || name.equals("OFF")) return 0;
        String[] names = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV",
            "MAX" };
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) return 8L * (long) Math.pow(4, i);
        }
        return 0;
    }

    public static String voltageToTierName(long voltage) {
        if (voltage <= 0) return "";
        int tier = (int) Math.round(Math.log(voltage / 8.0) / Math.log(4));
        String[] names = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV",
            "MAX" };
        return tier >= 0 && tier < names.length ? names[tier] : "T" + tier;
    }
}
