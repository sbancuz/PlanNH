package com.sbancuz.plannh.data.provider.gregtech.probe;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.Config;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.provider.gregtech.GTMachineOverrides;
import com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset;
import com.sbancuz.plannh.data.provider.gregtech.GTStructureTiers;
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
        if (!enabled() || OverclockInternals.RESOLVED == null) return null;
        final Class<?> machineClass = prototype.getClass();
        if (UNPROBEABLE.containsKey(machineClass)) return null;

        final GTMachinePreset cached = PROBED.get(machineClass);
        if (cached != null) return cached;

        final GTMachinePreset probed = build(prototype);
        if (probed == null) {
            UNPROBEABLE.put(machineClass, Boolean.TRUE);
        } else if (drivesNumbers()) {
            // Only worth keeping when a chart reads it. In shadow mode the answer feeds one log line,
            // and holding it would pin a cloned MetaTileEntity per machine for the client's lifetime.
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

        if (agrees(table, probed)) return;

        // A machine with a stated reason for not being read from GregTech is expected to disagree.
        // Saying so is the point: a name that appears here without a reason is the news.
        final String overridden = GTMachineOverrides.reason(machineClass);
        if (overridden != null) {
            PlanNH.LOG.debug("PlanNH probe: {} disagrees as expected, {}", machineClass.getName(), overridden);
            return;
        }
        final StructureState state = reference();
        PlanNH.LOG.info(
            "PlanNH probe: {} table {} vs probe {}",
            machineClass.getName(),
            headline(table, state),
            headline(probed, state));
    }

    /**
     * Whether the two describe the same machine, compared at the structure an untouched node shows.
     * Public because the machine table reports it too, and one comparison must serve both or the log
     * and the table can disagree about whether they disagree.
     */
    public static boolean agrees(@Nonnull final GTMachinePreset table, @Nonnull final GTMachinePreset probed) {
        final StructureState state = reference();
        return headline(table, state).matches(headline(probed, state));
    }

    /** Everything a wrong row shows up in. Heat belongs here: it drives overclocks as much as speed does. */
    private record Headline(int parallel, double duration, double eu, double ocDuration, double ocEut, int machineHeat,
        boolean heatOC, boolean heatDiscount, int recipeHeat, int tierSkips) {

        boolean matches(final Headline other) {
            return parallel == other.parallel && sameNumber(duration, other.duration)
                && sameNumber(eu, other.eu)
                && sameNumber(ocDuration, other.ocDuration)
                && sameNumber(ocEut, other.ocEut)
                && machineHeat == other.machineHeat
                && heatOC == other.heatOC
                && heatDiscount == other.heatDiscount
                && recipeHeat == other.recipeHeat
                && tierSkips == other.tierSkips;
        }

        private static boolean sameNumber(final double table, final double probed) {
            return Math.abs(table - probed) <= SAME_NUMBER * Math.max(1, Math.abs(table));
        }
    }

    @Nonnull
    private static Headline headline(final GTMachinePreset preset, final StructureState state) {
        return new Headline(
            preset.maxParallel()
                .applyAsInt(state),
            preset.durationModifier()
                .applyAsDouble(state),
            preset.euModifier()
                .applyAsDouble(state),
            preset.durationDecreasePerOC()
                .applyAsDouble(state),
            preset.eutIncreasePerOC()
                .applyAsDouble(state),
            preset.usesHeat() ? preset.machineHeat()
                .applyAsInt(state) : 0,
            preset.heatOC(),
            preset.heatDiscount(),
            preset.recipeHeatOverride(),
            effectiveTierSkips(preset));
    }

    /**
     * What the calculator ends up with. An unset preset never calls the setter, so it lands on
     * GregTech's default of one - which is not the same as a machine that pinned zero.
     */
    private static int effectiveTierSkips(final GTMachinePreset preset) {
        if (preset.unlimitedTierSkips()) return Integer.MAX_VALUE;
        return preset.maxTierSkips() == GTMachinePreset.TIER_SKIPS_UNSET ? DEFAULT_TIER_SKIPS : preset.maxTierSkips();
    }

    /**
     * The structure the probe reads a machine's flags at: every setting at the best available, matching
     * what {@code GTSettings.resolve} hands an untouched node. Whether a machine overclocks on heat or
     * rewrites its recipe cost is structural rather than tiered, so the voltage here is only the
     * lowest real one.
     */
    @Nonnull
    private static StructureState reference() {
        return new StructureState(
            1,
            GTStructureTiers.MAX_COIL_TIER,
            GTStructureTiers.MAX_SOLENOID_TIER,
            GTStructureTiers.MAX_ITEM_PIPE_TIER,
            GTStructureTiers.MAX_PIPE_CASING_TIER,
            GTStructureTiers.MAX_SAWBLADE_TIER,
            0,
            2,
            GTStructureTiers.MAX_WIDTH,
            0);
    }

    @Nullable
    private static GTMachinePreset build(final IMetaTileEntity prototype) {
        final ProbeSubject subject = ProbeSubject.of(prototype);
        if (subject == null) return null;
        final GTRecipe recipe = sentinel();
        if (recipe == null) return null;
        final ProbeReading reference = subject.read(reference(), recipe);
        if (reference == null) return null;
        if (!reference.isRunnable()) {
            PlanNH.LOG.debug("PlanNH probe: {} reads as unrunnable, {}", prototype.getClass(), reference);
            return null;
        }
        // A state the machine declines falls back to the reference rather than to nonsense.
        final Function<StructureState, ProbeReading> readings = state -> {
            final ProbeReading at = subject.read(state, recipe);
            return at != null && at.isRunnable() ? at : reference;
        };
        final EnumSet<Settings> settings = SensitivityScan
            .scan(reference(), subject.reachableSettings(), subject.modeCount(), readings);
        return toPreset(reference, readings, settings);
    }

    /**
     * Builds the preset. The numbers are functions because they are re-probed per structure; the flags
     * are read once at the reference state, because a machine does not start or stop overclocking on
     * heat depending on which coil is in it.
     */
    @Nonnull
    public static GTMachinePreset toPreset(@Nonnull final ProbeReading reference,
        @Nonnull final Function<StructureState, ProbeReading> readings, @Nonnull final EnumSet<Settings> settings) {
        final GTMachinePreset.Builder preset = GTMachinePreset.builder()
            .parallel(
                s -> Math.max(
                    1,
                    readings.apply(s)
                        .maxParallel()))
            .speed(
                s -> readings.apply(s)
                    .durationModifier())
            .eu(
                s -> readings.apply(s)
                    .euModifier())
            .overclock(
                s -> readings.apply(s)
                    .durationDecreasePerOC(),
                s -> readings.apply(s)
                    .eutIncreasePerOC())
            .settings(settings.toArray(new Settings[0]));

        // GregTech sets a machine heat even where it never overclocks on one, so record it either way
        // and let the flags decide whether the applier hands it to the calculator.
        preset.machineHeat(
            s -> readings.apply(s)
                .machineHeat());
        if (reference.heatOC()) preset.heatOC(
            s -> readings.apply(s)
                .machineHeat());
        if (reference.heatDiscount()) preset.heatDiscount();
        // Only a machine that overclocks on heat has a heat floor to pin. The rest leave the field at
        // GT's zero, which would otherwise read as "this machine fires from absolute zero".
        // Among those that do: passing the recipe's own value through leaves the sentinel intact,
        // which is what RECIPE_HEAT_FROM_RECIPE already means.
        if (reference.usesHeat() && reference.recipeHeat() != SENTINEL_HEAT) {
            preset.recipeHeat(reference.recipeHeat());
        }

        if (reference.maxTierSkip() == Integer.MAX_VALUE) {
            preset.unlimitedTierSkips();
        } else if (reference.maxTierSkip() != DEFAULT_TIER_SKIPS) {
            // One is what a calculator starts at, so a machine reporting it said nothing. Leaving the
            // preset unset means the applier does not call the setter either, which is the same thing.
            preset.maxTierSkips(reference.maxTierSkip());
        }

        if (reference.recipeEUt() != SENTINEL_EUT || reference.duration() != SENTINEL_DURATION) {
            preset.recipeOverride((int) reference.recipeEUt(), reference.duration());
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
