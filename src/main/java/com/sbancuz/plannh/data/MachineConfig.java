package com.sbancuz.plannh.data;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

public class MachineConfig {

    public String profileId;
    public final Map<String, Object> settings = new HashMap<>();
    public final Map<Integer, Float> inputConsumption = new HashMap<>();
    public final Map<Integer, Float> outputProductivity = new HashMap<>();

    public MachineConfig() {
        this(MachineProfileRegistry.get(MachineProfileRegistry.defaultId()));
    }

    public MachineConfig(final MachineProfile profile) {
        this.profileId = profile.id();
        for (final SettingDef<?> def : profile.settings()) {
            settings.putIfAbsent(def.key, def.defaultValue);
        }
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

    public String getString(final String key) {
        final Object v = settings.get(key);
        return v instanceof final String s ? s : "";
    }

    public void setInt(final String key, final int value) {
        settings.put(key, value);
    }

    public void setBoolean(final String key, final boolean value) {
        settings.put(key, value);
    }

    public void setString(final String key, final String value) {
        settings.put(key, value);
    }

    @Nonnull
    public MachineProfile.EffectResult computeEffect(final Map<RecipeProperty<?>, Object> properties,
        final int recipeDuration) {
        final MachineProfile profile = getProfile();
        MachineProfile.EffectResult result = profile.effectComputer()
            .compute(settings, new MachineProfile.RecipeContext(properties, recipeDuration));
        final int tickMod = MachineProfile.getInt(settings, Settings.TICK_MODIFIER.key(), 100);
        if (tickMod > 0 && tickMod != 100) {
            final double factor = 100.0 / tickMod;
            final int newDuration = Math.max(1, (int) Math.round(result.durationTicks() * factor));
            final long newEnergyPerT = Math.round(result.energyPerT() / factor);
            result = new MachineProfile.EffectResult(newDuration, newEnergyPerT, result.throughputFactor());
        }
        return result;
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
            final Object val = settings.get(def.key);
            if (val != null && !val.equals(def.defaultValue)) return true;
        }
        return !inputConsumption.isEmpty() || !outputProductivity.isEmpty();
    }
}
