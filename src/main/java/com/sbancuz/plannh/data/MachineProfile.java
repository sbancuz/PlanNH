package com.sbancuz.plannh.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.effect.EffectComputer;
import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.setting.SettingDef;
import com.sbancuz.plannh.data.setting.Settings;
import com.sbancuz.plannh.nei.NEIPlanConfig;

import codechicken.nei.NEIClientConfig;

public record MachineProfile(String id, String displayName, List<SettingDef<?>> settings,
    EffectComputer effectComputer) {

    @Nonnull
    public static Builder builder(final String id, final String displayName) {
        return new Builder(id, displayName);
    }

    public static class Builder {

        private final String id;
        private final String displayName;
        private final List<SettingDef<?>> settings = new ArrayList<>();
        private EffectComputer effectComputer = (s, ctx) -> {
            Object dur = ctx.properties()
                .get(RecipePropertyAPI.DURATION_TICKS);
            return new EffectResult(dur instanceof Number n ? n.intValue() : 0, 0, 1);
        };

        private Builder(final String id, final String displayName) {
            this.id = id;
            this.displayName = displayName;
            if (NEIClientConfig.getSetting(NEIPlanConfig.ConfigBurnableOverride.KEY)
                .getIntValue(NEIPlanConfig.ConfigBurnableOverride.OFF) == NEIPlanConfig.ConfigBurnableOverride.ON) {
                addSetting(Settings.BURNABLE_OVERRIDE.def());
            }
        }

        public Builder addSetting(final SettingDef<?> setting) {
            settings.add(setting);
            return this;
        }

        public Builder setting(final SettingDef<?> s) {
            return addSetting(s);
        }

        public Builder settings(final Consumer<Builder> consumer) {
            consumer.accept(this);
            return this;
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

    @Nonnull
    public Stream<SettingDef<?>> visibleSettings(final RecipeContext ctx, final Map<String, Object> machineSettings) {
        return settings.stream()
            .filter(def -> def.isVisible(ctx, machineSettings));
    }
}
