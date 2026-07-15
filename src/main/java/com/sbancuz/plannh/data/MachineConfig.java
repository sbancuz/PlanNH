package com.sbancuz.plannh.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.data.setting.SettingDef;
import com.sbancuz.plannh.data.setting.Settings;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MachineConfig {

    private String profileId;
    private final Map<String, Object> settings;
    private final Map<Integer, Float> inputConsumption;
    private final Map<Integer, Float> outputProductivity;

    public MachineConfig() {
        this(MachineProfileRegistry.get(MachineProfileRegistry.defaultId()));
    }

    public MachineConfig(final MachineProfile profile) {
        this(profile, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    public MachineConfig(final MachineProfile profile, Map<String, Object> settings,
        Map<Integer, Float> inputConsumption, Map<Integer, Float> outputProductivity) {
        this.profileId = profile.id();
        this.inputConsumption = new HashMap<>(inputConsumption);
        this.outputProductivity = new HashMap<>(outputProductivity);
        this.settings = new HashMap<>(settings);

        for (SettingDef<?> def : profile.settings()) this.settings.putIfAbsent(def.getKey(), def.getDefaultValue());
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
            final Object val = settings.get(def.getKey());
            if (val != null && !val.equals(def.getDefaultValue())) return true;
        }
        return !inputConsumption.isEmpty() || !outputProductivity.isEmpty();
    }
}
