package com.sbancuz.plannh.data.provider.gregtech.probe;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.Config;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.GTRecipe;

/**
 * Asks a GregTech multiblock what it would do with a recipe, and turns the answer into the same
 * {@link GTMachinePreset} the hand-written table produces.
 *
 * <p>
 * The point is that the arithmetic stays in GregTech. A machine's parallel count, speed, EU discount,
 * overclock factors and heat behaviour are read off the {@code OverclockCalculator} its own processing
 * logic builds, so a pack running a GregTech that PlanNH was never compiled against still gets that
 * version's numbers instead of a transcription of an older one.
 *
 * <p>
 * This probe reads a machine in its unbuilt state, so what it returns does not yet vary with the coil
 * or pipe casing the player would place. Machines whose numbers move with their structure therefore
 * still need their table row; the shadow mode exists to find out which those are.
 */
public final class MachineProbe {

    private MachineProbe() {}

    /**
     * Values no recipe would carry, so a calculator field that no longer holds one was overwritten by
     * the machine rather than passed through.
     */
    public static final int SENTINEL_EUT = 7919;
    public static final int SENTINEL_DURATION = 6271;
    public static final int SENTINEL_HEAT = 4242;

    /** What {@code OverclockCalculator} starts at, so reading it back means the machine set nothing. */
    private static final int DEFAULT_TIER_SKIPS = 1;

    /** GregTech computes its modifiers in float and the preset table in double, so the last bits differ. */
    private static final double SAME_NUMBER = 1e-6;

    /**
     * Keyed by machine class and never invalidated, because a machine's answer cannot change without
     * the pack changing. Static because GregTech's prototype registry is: there is one set of machines
     * per client, and the probe has no owner with a shorter life than that.
     */
    private static final Map<Class<?>, GTMachinePreset> PROBED = new HashMap<>();
    private static final Map<Class<?>, Boolean> UNPROBEABLE = new HashMap<>();

    /** Built lazily: GTRecipe's constructor reaches the ore dictionary, which is not up at class-load. */
    @Nullable
    private static GTRecipe sentinel;

    /** Whether the probe's numbers reach a chart, or only the log. */
    public static boolean drivesNumbers() {
        return "on".equals(Config.gtProbeMode);
    }

    private static boolean shadowing() {
        return "shadow".equals(Config.gtProbeMode);
    }

    private static boolean enabled() {
        return drivesNumbers() || shadowing();
    }

    /**
     * The probed preset for a machine, or null when it has nothing to read or the probe is off. Cached
     * per machine class: the answer cannot change without the pack changing.
     */
    @Nullable
    public static GTMachinePreset probe(@Nonnull final IMetaTileEntity prototype) {
        if (!enabled() || ProbeFields.RESOLVED == null) return null;
        final Class<?> machineClass = prototype.getClass();
        if (UNPROBEABLE.containsKey(machineClass)) return null;

        final GTMachinePreset cached = PROBED.get(machineClass);
        if (cached != null) return cached;

        final GTMachinePreset probed = build(prototype);
        if (probed == null) {
            UNPROBEABLE.put(machineClass, Boolean.TRUE);
        } else {
            PROBED.put(machineClass, probed);
        }
        return probed;
    }

    /**
     * Logs where the probe and the hand-written table disagree. Shadow mode's whole purpose: a row may
     * only be deleted from the table once this says nothing about it.
     */
    public static void reportDisagreement(@Nonnull final Class<?> machineClass, @Nullable final GTMachinePreset table,
        @Nullable final GTMachinePreset probed) {
        if (!shadowing()) return;
        if (table == null || probed == null) {
            PlanNH.LOG.debug(
                "PlanNH probe: {} is covered by {} only",
                machineClass.getName(),
                table == null ? "the probe" : "the table");
            return;
        }

        // The unbuilt state is all the probe can see today, so that is where the two are compared.
        final StructureState state = new StructureState(1, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        final boolean same = table.maxParallel()
            .applyAsInt(state)
            == probed.maxParallel()
                .applyAsInt(state)
            && sameNumber(
                table.durationModifier()
                    .applyAsDouble(state),
                probed.durationModifier()
                    .applyAsDouble(state))
            && sameNumber(
                table.euModifier()
                    .applyAsDouble(state),
                probed.euModifier()
                    .applyAsDouble(state));
        if (!same) {
            PlanNH.LOG.info(
                "PlanNH probe: {} table {} vs probe {}",
                machineClass.getName(),
                headline(table, state),
                headline(probed, state));
        }
    }

    private static boolean sameNumber(final double table, final double probed) {
        return Math.abs(table - probed) <= SAME_NUMBER * Math.max(1, Math.abs(table));
    }

    /** Parallel, duration multiplier and EU discount - the three a wrong row shows up in first. */
    @Nonnull
    private static String headline(final GTMachinePreset preset, final StructureState state) {
        return preset.maxParallel()
            .applyAsInt(state) + "x "
            + preset.durationModifier()
                .applyAsDouble(state)
            + "d "
            + preset.euModifier()
                .applyAsDouble(state)
            + "eu";
    }

    @Nullable
    private static GTMachinePreset build(final IMetaTileEntity prototype) {
        final ProbeSubject subject = ProbeSubject.of(prototype);
        if (subject == null) return null;
        final GTRecipe recipe = sentinel();
        if (recipe == null) return null;
        final ProbeReading reading = subject.read(recipe);
        if (reading == null) return null;
        if (!reading.isRunnable()) {
            PlanNH.LOG.debug("PlanNH probe: {} reads as unrunnable unbuilt, {}", prototype.getClass(), reading);
            return null;
        }
        return toPreset(reading);
    }

    @Nonnull
    public static GTMachinePreset toPreset(@Nonnull final ProbeReading reading) {
        final GTMachinePreset.Builder preset = GTMachinePreset.builder()
            .parallel(Math.max(1, reading.maxParallel()))
            .speed(reading.durationModifier())
            .eu(reading.euModifier())
            .overclock(reading.durationDecreasePerOC(), reading.eutIncreasePerOC());

        if (reading.heatOC()) preset.heatOC(s -> reading.machineHeat());
        if (reading.heatDiscount()) {
            preset.heatDiscount();
            preset.machineHeat(s -> reading.machineHeat());
        }
        // Only a machine that overclocks on heat has a heat floor to pin. The rest leave the field at
        // GT's zero, which would otherwise read as "this machine fires from absolute zero".
        // Among those that do: passing the recipe's own value through leaves the sentinel intact,
        // which is what RECIPE_HEAT_FROM_RECIPE already means.
        if (reading.usesHeat() && reading.recipeHeat() != SENTINEL_HEAT) {
            preset.recipeHeat(reading.recipeHeat());
        }

        if (reading.maxTierSkip() == Integer.MAX_VALUE) {
            preset.unlimitedTierSkips();
        } else if (reading.maxTierSkip() != DEFAULT_TIER_SKIPS) {
            // One is what a calculator starts at, so a machine reporting it said nothing. Leaving the
            // preset unset means the applier does not call the setter either, which is the same thing.
            preset.maxTierSkips(reading.maxTierSkip());
        }

        if (reading.recipeEUt() != SENTINEL_EUT || reading.duration() != SENTINEL_DURATION) {
            preset.recipeOverride((int) reading.recipeEUt(), reading.duration());
        }
        return preset.build();
    }

    @Nullable
    private static GTRecipe sentinel() {
        if (sentinel == null) {
            try {
                sentinel = new GTRecipe(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    SENTINEL_DURATION,
                    SENTINEL_EUT,
                    SENTINEL_HEAT);
            } catch (final RuntimeException | LinkageError e) {
                PlanNH.LOG.warn("PlanNH: cannot build a probe recipe, machine probing is off", e);
                return null;
            }
        }
        return sentinel;
    }
}
