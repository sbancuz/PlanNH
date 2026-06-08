package com.sbancuz.plannh.data;

import java.util.HashMap;
import java.util.Map;

public class MachineConfig {

    public String profileId = MachineProfileRegistry.defaultId();
    public final Map<String, Object> settings = new HashMap<>();
    public final Map<Integer, Float> inputConsumption = new HashMap<>();
    public final Map<Integer, Float> outputProductivity = new HashMap<>();

    public MachineProfile getProfile() {
        MachineProfile p = MachineProfileRegistry.get(profileId);
        return p != null ? p : MachineProfileRegistry.get(MachineProfileRegistry.defaultId());
    }

    public int getInt(String key) {
        Object v = settings.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    public boolean getBoolean(String key) {
        Object v = settings.get(key);
        return v instanceof Boolean b && b;
    }

    public String getString(String key) {
        Object v = settings.get(key);
        return v instanceof String s ? s : "";
    }

    public void setInt(String key, int value) {
        settings.put(key, value);
    }

    public void setBoolean(String key, boolean value) {
        settings.put(key, value);
    }

    public void setString(String key, String value) {
        settings.put(key, value);
    }

    public void initDefaults() {
        MachineProfile p = getProfile();
        if (p == null) return;
        for (SettingDef<?> def : p.settings()) {
            settings.putIfAbsent(def.key, def.defaultValue);
        }
    }

    public MachineProfile.EffectResult computeEffect(Map<RecipeProperty<?>, Object> properties, int recipeDuration) {
        MachineProfile profile = getProfile();
        if (profile == null) return new MachineProfile.EffectResult(0, 0, 1);
        return profile.effectComputer()
            .compute(settings, new MachineProfile.RecipeContext(properties, recipeDuration));
    }

    public float inputMultiplier(int inputIndex) {
        return inputConsumption.getOrDefault(inputIndex, 1.0f);
    }

    public float outputMultiplier(int outputIndex) {
        return outputProductivity.getOrDefault(outputIndex, 1.0f);
    }

    public boolean hasAnyBoost() {
        if (!MachineProfileRegistry.defaultId()
            .equals(profileId)) return true;
        MachineProfile p = getProfile();
        if (p != null) {
            for (SettingDef<?> def : p.settings()) {
                Object val = settings.get(def.key);
                if (val != null && !val.equals(def.defaultValue)) return true;
            }
        }
        return !inputConsumption.isEmpty() || !outputProductivity.isEmpty();
    }

    public MachineConfig copy() {
        MachineConfig c = new MachineConfig();
        c.profileId = profileId;
        c.settings.putAll(settings);
        c.inputConsumption.putAll(inputConsumption);
        c.outputProductivity.putAll(outputProductivity);
        return c;
    }
}
