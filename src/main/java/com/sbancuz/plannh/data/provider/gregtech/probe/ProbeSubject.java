package com.sbancuz.plannh.data.provider.gregtech.probe;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.PlanNH;

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

    private final MTEMultiBlockBase machine;
    private final ProcessingLogic logic;
    private final Method createCalculator;

    private ProbeSubject(final MTEMultiBlockBase machine, final ProcessingLogic logic, final Method createCalculator) {
        this.machine = machine;
        this.logic = logic;
        this.createCalculator = createCalculator;
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

    @Nullable
    ProbeReading read(@Nonnull final GTRecipe recipe) {
        final ProbeFields fields = ProbeFields.RESOLVED;
        if (fields == null) return null;
        try {
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
