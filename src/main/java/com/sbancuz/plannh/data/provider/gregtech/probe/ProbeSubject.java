package com.sbancuz.plannh.data.provider.gregtech.probe;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.provider.gregtech.GTMachineModes;
import com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;

/**
 * One machine, cloned out of GregTech's prototype registry, kept around to be asked what it would do
 * with a recipe.
 *
 * <p>
 * The clone matters. {@code setupProcessingLogic} writes the machine into its own processing logic
 * and later steps write structure state into instance fields, so probing the registry prototype
 * would corrupt the object GregTech hands to every placed block and to NEI. A clone that fails to
 * build is a machine the probe declines rather than one it probes in place.
 *
 * <p>
 * What comes back is GregTech's own arithmetic: the calculator is the one the machine's processing
 * logic builds, including whatever its subclass overrode.
 */
final class ProbeSubject {

    /**
     * Readings are memoized because the settings panel asks for them from visibility predicates that
     * run while the screen draws. The cap is a backstop against a chart walking a wide grid of states;
     * dropping the lot is fine, every entry is reproducible.
     */
    private static final int MAX_CACHED_READINGS = 512;

    private final MTEMultiBlockBase machine;
    private final ProcessingLogic logic;
    private final Method createCalculator;
    private final FieldInjector injector;
    private final Map<StructureState, ProbeReading> readings = new HashMap<>();

    /** The recipe every cached reading answers for. */
    @Nullable
    private GTRecipe cachedFor;

    private ProbeSubject(final MTEMultiBlockBase machine, final ProcessingLogic logic, final Method createCalculator) {
        this.machine = machine;
        this.logic = logic;
        this.createCalculator = createCalculator;
        this.injector = FieldInjector.forClass(machine.getClass());
    }

    /** The knobs this machine stores at all - not yet whether any of them changes a number. */
    @Nonnull
    EnumSet<Knob> reachableKnobs() {
        return injector.reachableKnobs();
    }

    /**
     * How many modes this machine has. The clone is already ours to write to, so the walk happens on
     * it rather than on a second copy.
     */
    int modeCount() {
        return GTMachineModes.of(machine)
            .count();
    }

    /** Null for anything without processing logic to read: singleblocks, and the machines that hand-roll checkProcessing. */
    @Nullable
    static ProbeSubject of(@Nonnull final IMetaTileEntity prototype) {
        final ProbeFields fields = ProbeFields.RESOLVED;
        if (fields == null || !(prototype instanceof MTEMultiBlockBase)) return null;
        try {
            final IMetaTileEntity clone = prototype.newMetaEntity(null);
            if (!(clone instanceof final MTEMultiBlockBase multi)) return null;
            final Object logic = fields.machineLogic.get(multi);
            if (!(logic instanceof final ProcessingLogic processing)) return null;
            return new ProbeSubject(multi, processing, ProbeFields.overclockCalculatorOf(processing));
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
            PlanNH.LOG.debug("PlanNH: cannot clone {} for probing", prototype.getClass(), e);
            return null;
        }
    }

    /**
     * What the machine would do with this recipe, built into this structure. Null when it declined to
     * answer, which the caller reads as "this machine keeps its hand-written row".
     */
    @Nullable
    ProbeReading read(@Nonnull final StructureState state, @Nonnull final GTRecipe recipe) {
        // Readings are keyed by structure alone, so a different recipe invalidates all of them. Today
        // only the probe's own recipe is ever passed, but a caller that passes the node's real recipe
        // would otherwise be served the answer to a question it did not ask.
        if (recipe != cachedFor) {
            readings.clear();
            cachedFor = recipe;
        }

        final ProbeReading cached = readings.get(state);
        if (cached != null) return cached;
        if (readings.size() >= MAX_CACHED_READINGS) readings.clear();

        final ProbeReading reading = measure(state, recipe);
        if (reading != null) readings.put(state, reading);
        return reading;
    }

    @Nullable
    private ProbeReading measure(final StructureState state, final GTRecipe recipe) {
        final ProbeFields fields = ProbeFields.RESOLVED;
        if (fields == null) return null;
        try {
            injector.apply(machine, state);
            // Voltage is not a field the machine holds; it counts it off its energy hatches, so a
            // machine that scales per tier answers for tier zero until it has one.
            if (!FakeEnergyHatch.attach(machine, state.voltageTier())) {
                PlanNH.LOG.debug("PlanNH: {} would not take a probe energy hatch", machine.getClass());
                return null;
            }
            fields.setupProcessingLogic.invoke(machine, logic);
            resolveSuppliers(fields);
            final OverclockCalculator calculator = (OverclockCalculator) createCalculator.invoke(logic, recipe);
            return new ProbeReading(
                fields.maxParallel.getInt(logic),
                fields.calcDurationModifier.getDouble(calculator),
                fields.calcEutModifier.getDouble(calculator),
                fields.calcEutIncreasePerOC.getDouble(calculator),
                fields.calcDurationDecreasePerOC.getDouble(calculator),
                fields.calcMaxTierSkip.getInt(calculator),
                fields.calcHeatOC.getBoolean(calculator),
                fields.calcHeatDiscount.getBoolean(calculator),
                fields.calcMachineHeat.getInt(calculator),
                fields.calcRecipeHeat.getInt(calculator),
                fields.calcRecipeEUt.getLong(calculator),
                fields.calcDuration.getInt(calculator),
                fields.calcNoOverclock.getBoolean(calculator),
                fields.calcLaserOC.getBoolean(calculator));
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
            // Some machines reach for world state from setupProcessingLogic - the Circuit Assembly
            // Line dereferences its imprint - and a world-less clone has none.
            PlanNH.LOG.debug("PlanNH: {} declined to be probed", machine.getClass(), e);
            return null;
        }
    }

    /**
     * ProcessingLogic resolves its three suppliers in {@code process()}, which needs inventories, so
     * the probe does that step itself. Without it every machine that scales with its structure reads
     * as whatever the constructor happened to set.
     */
    private void resolveSuppliers(final ProbeFields fields) throws ReflectiveOperationException {
        final Object parallel = fields.maxParallelSupplier.get(logic);
        if (parallel != null) fields.maxParallel.setInt(logic, (Integer) ((Supplier<?>) parallel).get());

        final Object eu = fields.euModSupplier.get(logic);
        if (eu != null) fields.euModifier.setDouble(logic, (Double) ((Supplier<?>) eu).get());

        final Object speed = fields.speedBoostSupplier.get(logic);
        if (speed != null) fields.speedBoost.setDouble(logic, (Double) ((Supplier<?>) speed).get());
    }
}
