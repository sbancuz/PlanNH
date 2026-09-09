package com.sbancuz.plannh.data;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.setting.SettingDef;
import com.sbancuz.plannh.data.setting.Settings;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MachineConfig {

    private String profileId;
    private final Map<String, Object> settings;
    // todo make these functional
    private final Map<Integer, Float> inputConsumption = new HashMap<>();
    private final Map<Integer, Float> outputProductivity = new HashMap<>();

    public MachineConfig() {
        this(MachineProfileRegistry.get(MachineProfileRegistry.defaultId()));
    }

    public MachineConfig(@Nullable final MachineProfile requested) {
        this(requested, new HashMap<>());
    }

    public MachineConfig(@Nullable final MachineProfile requested, Map<String, Object> settings) {
        // An unknown profile id means the chart was saved with a mod (or a mod version) that is
        // not present now. That is a chart to degrade, not a save to lose: fall back to the
        // default profile, exactly as getProfile() does for the same reason.
        MachineProfile profile = requested != null ? requested
            : MachineProfileRegistry.get(MachineProfileRegistry.defaultId());
        profileId = profile.id();

        this.settings = new HashMap<>(settings);

        for (final SettingDef<?> def : profile.settings()) settings.putIfAbsent(def.getKey(), def.getDefaultValue());

        settings.putIfAbsent(
            Settings.MACHINES.key(),
            Settings.MACHINES.def()
                .getDefaultValue());
    }

    @Nonnull
    public MachineProfile getProfile() {
        final MachineProfile p = MachineProfileRegistry.get(profileId);
        return p != null ? p : MachineProfileRegistry.get(MachineProfileRegistry.defaultId());
    }

    public int getInt(final String key) {
        final Object v = settings.get(key);
        return v instanceof final Number n ? n.intValue() : 0;
    }

    public boolean getBoolean(final String key) {
        final Object v = settings.get(key);
        return v instanceof final Boolean b && b;
    }

    public <E extends Enum<E>> E getEnum(final String key, Class<E> type) {
        final Object v = settings.get(key);
        return type.isInstance(v) ? type.cast(v) : null;
    }

    public void setInt(final String key, final int value) {
        settings.put(key, value);
    }

    public void setBoolean(final String key, final boolean value) {
        settings.put(key, value);
    }

    public void setEnum(final String key, final Enum<?> value) {
        settings.put(key, value);
    }

    /**
     * Applies the profile's per-recipe-map route defaults (e.g. Perfect OC on for specific recipe
     * machines) to the settings. Only values still at the profile default are overridden, so a
     * user's explicit choice is never clobbered.
     */
    public void seedRouteDefaults(Map<RecipeProperty<?>, Object> properties) {
        final RecipeContext ctx = new RecipeContext(properties);
        final MachineProfile profile = getProfile();
        final Map<String, Object> defaults = profile.effectComputer()
            .routeDefaults(ctx);
        if (defaults.isEmpty()) return;
        for (final Map.Entry<String, Object> e : defaults.entrySet()) {
            final Object current = settings.get(e.getKey());
            if (current == null) {
                settings.put(e.getKey(), e.getValue());
                continue;
            }
            final Object profileDefault = profile.settings()
                .stream()
                .filter(
                    def -> def.getKey()
                        .equals(e.getKey()))
                .map(SettingDef::getDefaultValue)
                .findFirst()
                .orElse(null);
            if (current.equals(profileDefault)) {
                settings.put(e.getKey(), e.getValue());
            }
        }
    }

    @Nonnull
    public EffectResult computeEffect(final Map<RecipeProperty<?>, Object> properties) {
        final MachineProfile profile = getProfile();
        EffectResult result = profile.effectComputer()
            .compute(settings, new RecipeContext(properties));
        final int tickMod = MachineProfile.getInt(settings, Settings.TICK_MODIFIER.key(), 100);
        if (tickMod > 0 && tickMod != 100) {
            final double factor = 100.0 / tickMod;
            final int newDuration = Math.max(1, (int) Math.round(result.durationTicks() * factor));
            final long newEnergyPerT = Math.round(result.energyPerT() / factor);
            result = new EffectResult(newDuration, newEnergyPerT, result.throughputFactor());
        }
        return result;
    }

    /**
     * Takes another node's machine settings, for the members of a machine group: they are one
     * machine, so they run at one tier with one set of upgrades. The machine count is left alone -
     * it is how much of that machine each recipe asks for, not part of what the machine is.
     */
    public void copySettingsFrom(final MachineConfig other) {
        final Object count = settings.get(Settings.MACHINES.key());
        settings.clear();
        settings.putAll(other.settings);
        if (count != null) settings.put(Settings.MACHINES.key(), count);
    }

    public int getMachineCount() {
        return getInt(Settings.MACHINES.key());
    }

    public void setMachineCount(final int count) {
        settings.put(Settings.MACHINES.key(), count);
    }

    public float inputMultiplier(final int inputIndex) {
        return inputConsumption.getOrDefault(inputIndex, 1.0f);
    }

    public float outputMultiplier(final int outputIndex) {
        return outputProductivity.getOrDefault(outputIndex, 1.0f);
    }

    public boolean hasAnyBoost() {
        if (!MachineProfileRegistry.defaultId()
            .equals(profileId)) return true;
        final MachineProfile p = getProfile();
        for (final SettingDef<?> def : p.settings()) {
            final Object val = settings.get(def.getKey());
            if (val != null && !val.equals(def.getDefaultValue())) return true;
        }
        return !inputConsumption.isEmpty() || !outputProductivity.isEmpty();
    }
}
