package com.sbancuz.plannh.data;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Singular;

@Builder(builderClassName = "Builder")
public record MachineProfile(String id, String displayName, @Singular("setting") List<SettingDef<?>> settings,
    EffectComputer effectComputer) {

    @FunctionalInterface
    public interface EffectComputer {

        EffectResult compute(Map<String, Object> settings, RecipeContext ctx);
    }

    public record EffectResult(int durationTicks, long consumptionEUt, int throughputFactor) {}

    public record RecipeContext(long recipeEUt, int recipeDuration) {}

    public static Builder builder(String id, String displayName) {
        return new Builder().id(id)
            .displayName(displayName);
    }

    public static class Builder {

        public Builder baseSettings() {
            setting(
                SettingDef.enumDef(
                    "voltage",
                    "Tier",
                    "OFF",
                    List.of(
                        "OFF",
                        "ULV",
                        "LV",
                        "MV",
                        "HV",
                        "EV",
                        "IV",
                        "LuV",
                        "ZPM",
                        "UV",
                        "UHV",
                        "UEV",
                        "UIV",
                        "UMV",
                        "UXV",
                        "MAX")));
            setting(SettingDef.intDef("amp", "Amp", 1, 1, 64));
            setting(SettingDef.intDef("speed", "Speed", 100, 10, 10000));
            setting(SettingDef.intDef("parallels", "Par", 1, 1, 4096));
            setting(SettingDef.intDef("machines", "Mach", 1, 1, 4096));
            setting(SettingDef.boolDef("perfectOC", "Perfect OC", false));
            return this;
        }

        public Builder heatSettings() {
            setting(SettingDef.intDef("machineHeat", "M. Heat", 0, 0, 100000));
            setting(SettingDef.intDef("recipeHeat", "R. Heat", 0, 0, 100000));
            setting(SettingDef.boolDef("heatOC", "Heat OC", true));
            setting(SettingDef.boolDef("heatDiscount", "Heat Disc.", false));
            setting(SettingDef.intDef("heatDiscountMult", "HD Mult.%", 100, 0, 200));
            return this;
        }

        public Builder advancedSettings() {
            setting(SettingDef.boolDef("laserOC", "Laser OC", false));
            setting(SettingDef.intDef("eutDiscount", "EU Disc.%", 0, 0, 100));
            setting(SettingDef.intDef("eutIncreasePerOC", "EU%/OC", 400, 100, 1000));
            setting(SettingDef.intDef("durationDecreasePerOC", "Spd%/OC", 200, 100, 1000));
            setting(SettingDef.intDef("maxOverclocks", "Max OC", 0, 0, 64));
            setting(SettingDef.intDef("maxRegularOc", "Max Reg OC", 0, 0, 64));
            setting(SettingDef.intDef("maxTierSkips", "Max Skips", 0, 0, 10));
            setting(SettingDef.boolDef("unlimitedSkips", "Unl. Skips", false));
            setting(SettingDef.boolDef("noOverclock", "No OC", false));
            return this;
        }

        public Builder effect(EffectComputer computer) {
            this.effectComputer = computer;
            return this;
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
