package com.sbancuz.plannh.data;

import static com.sbancuz.plannh.data.provider.GTProvider.EU_PER_TICK;
import static com.sbancuz.plannh.data.provider.GTProvider.TOTAL_EU;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record MachineProfile(String id, String displayName, List<SettingDef<?>> settings,
    EffectComputer effectComputer) {

    @FunctionalInterface
    public interface EffectComputer {

        EffectResult compute(Map<String, Object> settings, RecipeContext ctx);
    }

    public record EffectResult(int durationTicks, long energyPerT, int throughputFactor) {}

    public record RecipeContext(Map<RecipeProperty<?>, Object> properties, int recipeDuration) {

        public long recipeEUt() {
            final Long euPerTick = get(EU_PER_TICK);
            if (euPerTick != null && euPerTick > 0) return euPerTick;
            final Long totalEu = get(TOTAL_EU);
            if (totalEu != null && totalEu > 0 && recipeDuration > 0) return totalEu / recipeDuration;
            return 0;
        }

        @SuppressWarnings("unchecked")
        @Nullable
        public <T> T get(final RecipeProperty<T> prop) {
            return (T) properties.get(prop);
        }
    }

    @Nonnull
    public static Builder builder(final String id, final String displayName) {
        return new Builder(id, displayName);
    }

    public static class Builder {

        private final String id;
        private final String displayName;
        private final List<SettingDef<?>> settings = new ArrayList<>();
        private EffectComputer effectComputer = (s, ctx) -> new EffectResult(ctx.recipeDuration(), ctx.recipeEUt(), 1);

        private Builder(final String id, final String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public Builder addSetting(final SettingDef<?> setting) {
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

        public Builder setting(final SettingDef<?> s) {
            return addSetting(s);
        }

        public Builder effect(final EffectComputer effect) {
            this.effectComputer = effect;
            return this;
        }

        @Nonnull
        public MachineProfile build() {
            return new MachineProfile(id, displayName, List.copyOf(settings), effectComputer);
        }
    }

    public static int getInt(final Map<String, Object> s, final String key, final int def) {
        final Object v = s.get(key);
        return v instanceof final Number n ? n.intValue() : def;
    }

    public static boolean getBool(final Map<String, Object> s, final String key, final boolean def) {
        final Object v = s.get(key);
        return v instanceof final Boolean b ? b : def;
    }

    public static String getString(final Map<String, Object> s, final String key, final String def) {
        final Object v = s.get(key);
        return v instanceof final String str ? str : def;
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
}
