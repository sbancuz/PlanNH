package com.sbancuz.plannh.data.provider.gregtech.probe;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.PlanNH;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;

/**
 * The GregTech internals the probe reaches into, resolved once.
 *
 * <p>
 * All or nothing on purpose: a single renamed field would otherwise leave the probe reading some
 * machines correctly and defaulting others, which looks like a working probe with a few odd numbers.
 * When resolution fails, {@link #RESOLVED} is null, the probe never runs, and every machine falls
 * back to the hand-written preset table.
 *
 * <p>
 * {@code GTProbeFieldsTest} asserts every name here against the GregTech on the test classpath, so a
 * rename fails the build rather than a player's chart.
 */
final class ProbeFields {

    @Nullable
    static final ProbeFields RESOLVED = resolve();

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

    private ProbeFields() throws ReflectiveOperationException {
        machineLogic = field(MTEMultiBlockBase.class, "processingLogic");
        setupProcessingLogic = MTEMultiBlockBase.class.getDeclaredMethod("setupProcessingLogic", ProcessingLogic.class);
        setupProcessingLogic.setAccessible(true);

        maxParallel = field(ProcessingLogic.class, "maxParallel");
        maxParallelSupplier = field(ProcessingLogic.class, "maxParallelSupplier");
        euModifier = field(ProcessingLogic.class, "euModifier");
        euModSupplier = field(ProcessingLogic.class, "euModSupplier");
        speedBoost = field(ProcessingLogic.class, "speedBoost");
        speedBoostSupplier = field(ProcessingLogic.class, "speedBoostSupplier");

        calcDurationModifier = field(OverclockCalculator.class, "durationModifier");
        calcEutModifier = field(OverclockCalculator.class, "eutModifier");
        calcEutIncreasePerOC = field(OverclockCalculator.class, "eutIncreasePerOC");
        calcDurationDecreasePerOC = field(OverclockCalculator.class, "durationDecreasePerOC");
        calcMaxTierSkip = field(OverclockCalculator.class, "maxTierSkip");
        calcHeatOC = field(OverclockCalculator.class, "heatOC");
        calcHeatDiscount = field(OverclockCalculator.class, "heatDiscount");
        calcMachineHeat = field(OverclockCalculator.class, "machineHeat");
        calcRecipeHeat = field(OverclockCalculator.class, "recipeHeat");
        calcRecipeEUt = field(OverclockCalculator.class, "recipeEUt");
        calcDuration = field(OverclockCalculator.class, "duration");
        calcNoOverclock = field(OverclockCalculator.class, "noOverclock");
        calcLaserOC = field(OverclockCalculator.class, "laserOC");
    }

    /**
     * Machines override {@code createOverclockCalculator} on an anonymous ProcessingLogic subclass -
     * that is where GT++ puts its heat overclocking - so the method is resolved against the instance,
     * not against ProcessingLogic.
     */
    @Nonnull
    static Method overclockCalculatorOf(@Nonnull final ProcessingLogic logic) throws ReflectiveOperationException {
        for (Class<?> c = logic.getClass(); c != null; c = c.getSuperclass()) {
            try {
                final Method found = c.getDeclaredMethod("createOverclockCalculator", GTRecipe.class);
                found.setAccessible(true);
                return found;
            } catch (final NoSuchMethodException keepWalking) {
                // The override lives further up; ProcessingLogic itself always declares it.
            }
        }
        throw new NoSuchMethodException("createOverclockCalculator on " + logic.getClass());
    }

    @Nonnull
    private static Field field(final Class<?> owner, final String name) throws ReflectiveOperationException {
        final Field found = owner.getDeclaredField(name);
        AccessibleObject.setAccessible(new AccessibleObject[] { found }, true);
        return found;
    }

    @Nullable
    private static ProbeFields resolve() {
        try {
            return new ProbeFields();
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
            PlanNH.LOG.warn("PlanNH: GregTech's overclock internals moved, machine probing is off", e);
            return null;
        }
    }
}
