package com.sbancuz.plannh.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
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

public record MachineProfile(String id, String displayName, List<Entry> settings, EffectComputer effectComputer) {

    /** One setting this profile offers, and the rule for when a machine running it shows the row. */
    public record Entry(SettingDef<?> def, BiPredicate<RecipeContext, MachineConfig> visible) {

        public boolean isVisible(final RecipeContext ctx, final MachineConfig config) {
            return visible.test(ctx, config);
        }
    }

    /** An entry with no rule attached: the profile offers it to every machine. */
    public static Entry entry(final SettingDef<?> def) {
        return new Entry(def, (_, _) -> true);
    }

    @Nonnull
    public static Builder builder(final String id, final String displayName) {
        return new Builder(id, displayName);
    }

    public static class Builder {

        private final String id;
        private final String displayName;
        private final List<Entry> settings = new ArrayList<>();
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
                addSetting(Settings.BURNABLE_OVERRIDE);
            }
        }

        public Builder addSetting(final SettingDef<?> setting) {
            return setting(setting);
        }

        public Builder setting(final SettingDef<?> def) {
            return setting(def, (_, _) -> true);
        }

        public Builder setting(final SettingDef<?> def, final BiPredicate<RecipeContext, MachineConfig> visible) {
            settings.add(new Entry(def, visible));
            return this;
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

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof final MachineProfile other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** The defs on offer, unfiltered: what a fresh config seeds from, and what a save lists. */
    @Nonnull
    public List<? extends SettingDef<?>> defs() {
        return settings.stream()
            .map(Entry::def)
            .toList();
    }

    /**
     * The settings this profile offers for one machine: its own list, minus the ones hidden for
     * this recipe and these values. Visibility is a question about the machine, so it answers
     * against the config rather than against a bag of values.
     *
     * <p>
     * Nothing here depends on which target is pinned. {@link Settings#MACHINES} is how many machines
     * the recipe runs on and the copies target is how many of them the chart runs; they are different
     * numbers, so neither hides the other.
     */
    @Nonnull
    public Stream<? extends SettingDef<?>> visibleSettings(final RecipeContext ctx, final MachineConfig config) {
        return settings.stream()
            .filter(e -> e.isVisible(ctx, config))
            .map(Entry::def);
    }
}
