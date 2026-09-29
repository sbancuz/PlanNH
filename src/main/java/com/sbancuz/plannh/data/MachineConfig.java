package com.sbancuz.plannh.data;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.setting.SettingDef;
import com.sbancuz.plannh.data.setting.Settings;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MachineConfig {

    private String profileId;
    @Getter(AccessLevel.NONE)
    private final Map<SettingDef<?>, Object> settings;
    // todo make these functional
    private final Map<Integer, Float> inputConsumption = new HashMap<>();
    private final Map<Integer, Float> outputProductivity = new HashMap<>();

    public MachineConfig() {
        this(MachineProfileRegistry.get(MachineProfileRegistry.defaultId()));
    }

    public MachineConfig(@Nullable final MachineProfile requested) {
        this(requested, Map.of());
    }

    /**
     * @param settings the values that differ from the profile's, e.g. as read back from a save.
     *                 Every profile default is filled in here, so a config is whole from the moment
     *                 it exists and reading a setting never has to invent one.
     */
    public MachineConfig(@Nullable final MachineProfile requested, final Map<SettingDef<?>, Object> settings) {
        // An unknown profile id means the chart was saved with a mod (or a mod version) that is
        // not present now. That is a chart to degrade, not a save to lose: fall back to the
        // default profile, exactly as getProfile() does for the same reason.
        MachineProfile profile = requested != null ? requested
            : MachineProfileRegistry.get(MachineProfileRegistry.defaultId());
        profileId = profile.id();

        this.settings = new HashMap<>(settings);

        for (final SettingDef<?> def : profile.defs()) this.settings.putIfAbsent(def, def.getDefaultValue());
        this.settings.putIfAbsent(Settings.MACHINES, Settings.MACHINES.getDefaultValue());
    }

    @Nonnull
    public MachineProfile getProfile() {
        final MachineProfile p = MachineProfileRegistry.get(profileId);
        // TODO
        return p != null ? p : MachineProfileRegistry.get(MachineProfileRegistry.defaultId());
    }

    @SuppressWarnings("unchecked")
    public <T> T get(final SettingDef<T> setting) {
        return (T) settings.computeIfAbsent(setting, SettingDef::getDefaultValue);
    }

    @SuppressWarnings("unchecked")
    public <T> void set(final SettingDef<T> setting, T value) {
        settings.put(setting, value);
    }

    /** Seeds the values that are still unset. A default never overwrites a choice already made. */
    public void applyDefaults(final Map<SettingDef<?>, Object> defaults) {
        defaults.forEach(settings::putIfAbsent);
    }

    /**
     * Applies the profile's per-recipe-map route defaults (e.g. Perfect OC on for specific recipe
     * machines) to the settings. Only values still at the profile default are overridden, so a
     * user's explicit choice is never clobbered.
     */
    public void seedRouteDefaults(Map<RecipeProperty<?>, Object> properties) {
        final Map<SettingDef<?>, Object> defaults = getProfile().effectComputer()
            .routeDefaults(new RecipeContext(properties));
        if (defaults.isEmpty()) return;

        for (final Map.Entry<SettingDef<?>, Object> e : defaults.entrySet()) {
            final SettingDef<?> def = e.getKey();
            final Object current = settings.get(def);
            if (current == null || current.equals(def.getDefaultValue())) settings.put(def, e.getValue());
        }
    }

    @Nonnull
    public EffectResult computeEffect(final Map<RecipeProperty<?>, Object> properties) {
        final MachineProfile profile = getProfile();
        EffectResult result = profile.effectComputer()
            .compute(this, new RecipeContext(properties));
        final int tickMod = get(Settings.TICK_MODIFIER);
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
        final int count = get(Settings.MACHINES);
        settings.clear();
        settings.putAll(other.settings);
        set(Settings.MACHINES, count);
    }

    public float inputMultiplier(final int inputIndex) {
        return inputConsumption.getOrDefault(inputIndex, 1.0f);
    }

    public float outputMultiplier(final int outputIndex) {
        return outputProductivity.getOrDefault(outputIndex, 1.0f);
    }
}
