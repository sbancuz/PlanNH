package com.sbancuz.plannh.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.effect.EffectComputer;
import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.nei.NEIPlanConfig;

import codechicken.nei.NEIClientConfig;

/**
 * @param onLoad runs once on a chart loaded from disk, before anything reads it. A profile whose
 *               meaning has changed uses this to migrate old settings. Without it, the keys of a chart
 *               saved by an older PlanNH are read under the new meaning, which changes its numbers. The
 *               map it receives is the saved settings, with no defaults seeded.
 */
public record MachineProfile(String id, String displayName, List<SettingDef<?>> settings, EffectComputer effectComputer,
    Consumer<Map<String, Object>> onLoad) {

    public MachineProfile(final String id, final String displayName, final List<SettingDef<?>> settings,
        final EffectComputer effectComputer) {
        this(id, displayName, settings, effectComputer, s -> {});
    }

    @Nonnull
    public static Builder builder(final String id, final String displayName) {
        return new Builder(id, displayName);
    }

    public static class Builder {

        private final String id;
        private final String displayName;
        private final List<SettingDef<?>> settings = new ArrayList<>();
        private EffectComputer effectComputer=(s,ctx)->{final Object dur=ctx.properties().get(RecipePropertyAPI.DURATION_TICKS);return new EffectResult(dur instanceof
        final Number n?n.intValue():0,0,1);
    };

    private Consumer<Map<String, Object>> onLoad = s -> {};

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

    public Builder onLoad(final Consumer<Map<String, Object>> hook) {
        this.onLoad = hook;
        return this;
    }

    @Nonnull
    public MachineProfile build() {
        return new MachineProfile(id, displayName, List.copyOf(settings), effectComputer, onLoad);
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

    /**
     * The rows a node renders. Serialization walks the node's settings map, not this list, so a hidden
     * value still round-trips.
     */
    @Nonnull
    public List<SettingDef<?>> visibleSettings(final RecipeContext ctx, final Map<String, Object> machineSettings) {
        return settings.stream()
            .filter(def -> def.isVisible(ctx, machineSettings))
            .toList();
    }

    /** The def behind a key, for controls outside the settings panel. */
    @Nullable
    public SettingDef<?> setting(final String key) {
        for (final SettingDef<?> def : settings) {
            if (def.key.equals(key)) return def;
        }
        return null;
    }
}
