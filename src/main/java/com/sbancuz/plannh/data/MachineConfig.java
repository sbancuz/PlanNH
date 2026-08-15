package com.sbancuz.plannh.data;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.RecipeProperty;

/**
 * A node's machine settings.
 *
 * <p>
 * {@link #settings} is <b>sparse</b>: a key is present only if the user deliberately chose that
 * value. Absence means "derive it" - from the machine, or from the setting's declared default - so
 * {@code containsKey} answers "did the user choose this?" without sentinel values. Seeding every
 * default here is what forced the old sentinels (0 meaning auto, or a value equal to the default
 * meaning untouched), each of which eventually said the wrong thing.
 *
 * <p>
 * The machine count is the one exception: the balancer writes its solved count back here every
 * frame, so its presence cannot mean a user choice. It has its own provenance in
 * {@link Node#isMachineCountFixed()} and its own slot in the save.
 */
public class MachineConfig {

    public String profileId;
    public final Map<String, Object> settings = new HashMap<>();
    // todo make these functional
    public final Map<Integer, Float> inputConsumption = new HashMap<>();
    public final Map<Integer, Float> outputProductivity = new HashMap<>();

    private final Node parentRef;

    /** Key lookup for the current profile; rebuilt when the node's profile changes. */
    @Nullable
    private Map<String, SettingDef<?>> defsByKey;
    @Nullable
    private String defsProfileId;

    public MachineConfig(final Node parentRef) {
        this(parentRef, MachineProfileRegistry.get(MachineProfileRegistry.defaultId()));
    }

    public MachineConfig(final Node parentRef, @Nullable final MachineProfile requested) {
        this.parentRef = parentRef;
        // An unknown profile id means the chart was saved with a mod (or a mod version) that is
        // not present now. That is a chart to degrade, not a save to lose: fall back to the
        // default profile, exactly as getProfile() does for the same reason.
        final MachineProfile profile = requested != null ? requested
            : MachineProfileRegistry.get(MachineProfileRegistry.defaultId());
        this.profileId = profile.id();
        settings.put(Settings.MACHINES.key(), Settings.MACHINES.def().defaultValue);
    }

    @Nonnull
    public MachineProfile getProfile() {
        final MachineProfile p = MachineProfileRegistry.get(profileId);
        return p != null ? p : MachineProfileRegistry.get(MachineProfileRegistry.defaultId());
    }

    /**
     * The declared default for a key, used when nothing is stored. Cached because the settings rows
     * and the node title look defs up every frame and a profile carries around thirty of them.
     */
    @Nullable
    private SettingDef<?> def(final String key) {
        final MachineProfile profile = getProfile();
        if (defsByKey == null || !profile.id()
            .equals(defsProfileId)) {
            final Map<String, SettingDef<?>> built = new HashMap<>();
            for (final SettingDef<?> def : profile.settings()) {
                built.put(def.key, def);
            }
            defsByKey = built;
            defsProfileId = profile.id();
        }
        return defsByKey.get(key);
    }

    public int getInt(final String key) {
        final Object v = settings.get(key);
        if (v instanceof final Number n) return n.intValue();
        final SettingDef<?> def = def(key);
        return def != null && def.defaultValue instanceof final Number n ? n.intValue() : 0;
    }

    public boolean getBoolean(final String key) {
        final Object v = settings.get(key);
        if (v instanceof final Boolean b) return b;
        final SettingDef<?> def = def(key);
        return def != null && def.defaultValue instanceof final Boolean b && b;
    }

    public String getString(final String key) {
        final Object v = settings.get(key);
        if (v instanceof final String s) return s;
        final SettingDef<?> def = def(key);
        return def != null && def.defaultValue instanceof final String s ? s : "";
    }

    public void setInt(final String key, final int value) {
        settings.put(key, value);
        parentRef.refresh();
    }

    public void setBoolean(final String key, final boolean value) {
        settings.put(key, value);
        parentRef.refresh();
    }

    public void setString(final String key, final String value) {
        settings.put(key, value);
        parentRef.refresh();
    }

    /** Returns a key to being machine-derived, which is the state the UI otherwise cannot reach. */
    public void clear(final String key) {
        settings.remove(key);
        parentRef.refresh();
    }

    /**
     * Drops everything the previous machine implied. A different machine has different coils, a
     * different parallel ceiling and different overclock rules, so carrying values across produces
     * numbers the new machine cannot actually reach. Voltage survives because it describes the power
     * supplied to the node rather than the machine itself.
     */
    public void resetForNewMachine() {
        settings.keySet()
            .removeIf(
                key -> !Settings.VOLTAGE.key()
                    .equals(key));
        settings.put(Settings.MACHINES.key(), Settings.MACHINES.def().defaultValue);
        parentRef.setMachineCountFixed(false);
    }

    /**
     * Applies the profile's per-recipe-map route defaults (e.g. Perfect OC on for specific recipe
     * machines) to the settings. Only values still at the profile default are overridden, so a
     * user's explicit choice is never clobbered.
     */
    public void seedRouteDefaults() {
        final RecipeContext ctx = new RecipeContext(parentRef.properties);
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
                .filter(def -> def.key.equals(e.getKey()))
                .map(def -> def.defaultValue)
                .findFirst()
                .orElse(null);
            if (profileDefault != null && current.equals(profileDefault)) {
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

    public int getMachineCount() {
        // Hard default rather than the profile's: a profile need not declare the setting, and a
        // count of zero would silently void the node.
        final Object v = settings.get(Settings.MACHINES.key());
        return v instanceof final Number n ? Math.max(1, n.intValue()) : 1;
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

    /**
     * Whether this node has anything worth writing to the save. The machine count is excluded: the
     * balancer rewrites it on every solve, so counting it would put a config block on every node in
     * the chart and make merely viewing one look like an edit.
     */
    public boolean hasStoredSettings() {
        if (!MachineProfileRegistry.defaultId()
            .equals(profileId)) return true;
        for (final String key : settings.keySet()) {
            if (!Settings.MACHINES.key()
                .equals(key)) return true;
        }
        return !inputConsumption.isEmpty() || !outputProductivity.isEmpty();
    }
}
