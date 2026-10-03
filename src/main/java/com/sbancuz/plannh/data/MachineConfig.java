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
 * {@link #settings} is <b>sparse</b>: a key is present only if the user chose that value. Absence means
 * "derive it" from the machine or from the setting's declared default, so {@code containsKey} returns
 * whether the user chose it. Seeding defaults here would need sentinels (0 for auto, a value equal to the
 * default for untouched), and a sentinel can't be told apart from a user who picks that value.
 */
public class MachineConfig {

    public String profileId;
    public final Map<String, Object> settings = new HashMap<>();
    // todo make these functional
    public final Map<Integer, Float> inputConsumption = new HashMap<>();
    public final Map<Integer, Float> outputProductivity = new HashMap<>();

    private final Node parentRef;

    /** Key lookup for the current profile, rebuilt when the node's profile changes. */
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
    }

    @Nonnull
    public MachineProfile getProfile() {
        final MachineProfile p = MachineProfileRegistry.get(profileId);
        return p != null ? p : MachineProfileRegistry.get(MachineProfileRegistry.defaultId());
    }

    /**
     * The declared default for a key, used when nothing is stored. Cached because the settings rows
     * and the node title look defs up every frame, and a profile has around thirty.
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

    /**
     * Unpins a setting so the machine's value applies again. The steppers can't reach absence, so
     * without this a row stepped away and back stays pinned at that number.
     */
    public void clear(final String key) {
        settings.remove(key);
        parentRef.refresh();
    }

    /**
     * Drops every setting except voltage. Another machine has different coils, parallel ceiling and
     * overclock rules, so carried-over values give numbers the new machine can't reach. Voltage is kept
     * because it is the power supplied to the node, not a property of the machine.
     */
    public void resetForNewMachine() {
        settings.keySet()
            .removeIf(
                key -> !Settings.VOLTAGE.key()
                    .equals(key));
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
            result = result.withTiming(newDuration, newEnergyPerT);
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
        // absence is a state: an unpinned node must not inherit the other's pin
        if (count != null) settings.put(Settings.MACHINES.key(), count);
        else settings.remove(Settings.MACHINES.key());
    }

    /**
     * The machine count of this node, 1 when not pinned. A pin is the presence of the key. An unpinned
     * node follows the solver's count, so it must not add a multiplier.
     */
    public int getMachineCount() {
        // hard default, not the profile's: the setting may be absent from it, and a zero count voids the node
        final Object v = settings.get(Settings.MACHINES.key());
        return v instanceof final Number n ? Math.max(1, n.intValue()) : 1;
    }

    /** Whether a count was typed in. A pinned node's count is a constraint for the solver, not a variable. */
    public boolean isMachineCountPinned() {
        return settings.containsKey(Settings.MACHINES.key());
    }

    public void clearMachineCount() {
        settings.remove(Settings.MACHINES.key());
        parentRef.refresh();
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

    /** Whether this node has anything worth writing to the save. */
    public boolean hasStoredSettings() {
        if (!MachineProfileRegistry.defaultId()
            .equals(profileId)) return true;
        return !settings.isEmpty() || !inputConsumption.isEmpty() || !outputProductivity.isEmpty();
    }
}
