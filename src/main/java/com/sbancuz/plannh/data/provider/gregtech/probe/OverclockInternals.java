package com.sbancuz.plannh.data.provider.gregtech.probe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.Reflect;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;

/**
 * Reads GregTech's overclock arithmetic out of the classes that hold it, resolved once. The other
 * half of the probe, {@link StructureWriter}, goes the other way and writes a structure in.
 *
 * <p>
 * All or nothing on purpose: a single renamed field would otherwise leave the probe reading some
 * machines correctly and defaulting others, which looks like a working probe with a few odd numbers.
 * When resolution fails, {@link #RESOLVED} is null, the probe never runs, and every machine falls
 * back to the hand-written preset table. {@code StructureWriter} takes the opposite line for the
 * same reason stated there, so the policy stays at each of them rather than in {@code Reflect}.
 *
 * <p>
 * {@code GTOverclockInternalsTest} asserts every name here against the GregTech on the test
 * classpath, so a rename fails the build rather than a player's chart. It counts these members too,
 * so a field added here without a row there fails as well.
 */
final class OverclockInternals {

    @Nullable
    static final OverclockInternals RESOLVED = resolve();

    /** Built by {@code MTEMultiBlockBase}'s constructor, so a prototype already carries one. */
    final Field machineLogic;
    final Method setupProcessingLogic;

    final Field maxParallel;
    final Field maxParallelSupplier;
    final Field euModifier;
    final Field euModSupplier;
    final Field speedBoost;
    final Field speedBoostSupplier;

    final Field calcDurationModifier;
    final Field calcEutModifier;
    final Field calcEutIncreasePerOC;
    final Field calcDurationDecreasePerOC;
    final Field calcMaxTierSkip;
    final Field calcHeatOC;
    final Field calcHeatDiscount;
    final Field calcMachineHeat;
    final Field calcRecipeHeat;
    final Field calcRecipeEUt;
    final Field calcDuration;
    final Field calcNoOverclock;
    final Field calcLaserOC;

    private OverclockInternals() throws ReflectiveOperationException {
        machineLogic = Reflect.field(MTEMultiBlockBase.class, "processingLogic");
        setupProcessingLogic = Reflect
            .accessible(MTEMultiBlockBase.class.getDeclaredMethod("setupProcessingLogic", ProcessingLogic.class));

        maxParallel = Reflect.field(ProcessingLogic.class, "maxParallel");
        maxParallelSupplier = Reflect.field(ProcessingLogic.class, "maxParallelSupplier");
        euModifier = Reflect.field(ProcessingLogic.class, "euModifier");
        euModSupplier = Reflect.field(ProcessingLogic.class, "euModSupplier");
        speedBoost = Reflect.field(ProcessingLogic.class, "speedBoost");
        speedBoostSupplier = Reflect.field(ProcessingLogic.class, "speedBoostSupplier");

        calcDurationModifier = Reflect.field(OverclockCalculator.class, "durationModifier");
        calcEutModifier = Reflect.field(OverclockCalculator.class, "eutModifier");
        calcEutIncreasePerOC = Reflect.field(OverclockCalculator.class, "eutIncreasePerOC");
        calcDurationDecreasePerOC = Reflect.field(OverclockCalculator.class, "durationDecreasePerOC");
        calcMaxTierSkip = Reflect.field(OverclockCalculator.class, "maxTierSkip");
        calcHeatOC = Reflect.field(OverclockCalculator.class, "heatOC");
        calcHeatDiscount = Reflect.field(OverclockCalculator.class, "heatDiscount");
        calcMachineHeat = Reflect.field(OverclockCalculator.class, "machineHeat");
        calcRecipeHeat = Reflect.field(OverclockCalculator.class, "recipeHeat");
        calcRecipeEUt = Reflect.field(OverclockCalculator.class, "recipeEUt");
        calcDuration = Reflect.field(OverclockCalculator.class, "duration");
        calcNoOverclock = Reflect.field(OverclockCalculator.class, "noOverclock");
        calcLaserOC = Reflect.field(OverclockCalculator.class, "laserOC");
    }

    /**
     * Machines override {@code createOverclockCalculator} on an anonymous ProcessingLogic subclass -
     * that is where GT++ puts its heat overclocking - so the method is resolved against the instance,
     * not against ProcessingLogic.
     */
    @Nonnull
    static Method overclockCalculatorOf(@Nonnull final ProcessingLogic logic) throws ReflectiveOperationException {
        final Method found = Reflect
            .declaredMethod(logic.getClass(), null, "createOverclockCalculator", GTRecipe.class);
        if (found == null) throw new NoSuchMethodException("createOverclockCalculator on " + logic.getClass());
        return found;
    }

    @Nullable
    private static OverclockInternals resolve() {
        try {
            return new OverclockInternals();
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
            PlanNH.LOG.warn("PlanNH: GregTech's overclock internals moved, machine probing is off", e);
            return null;
        }
    }
}
