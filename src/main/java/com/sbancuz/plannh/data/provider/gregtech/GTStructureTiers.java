package com.sbancuz.plannh.data.provider.gregtech;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.PlanNH;

import gregtech.api.enums.HeatingCoilLevel;

/**
 * How far each structure knob goes, and the per-tier numbers GregTech attaches to the two knobs that
 * carry their own parameter tables.
 *
 * <p>
 * Everything GregTech exposes is read from GregTech, so a pack running a different GT version gets
 * that version's numbers rather than the ones a PlanNH release was written against. The rest is
 * declared here because GT keeps it in instance methods on registered blocks or in private
 * registries filled at mod init, neither of which a class can read.
 *
 * <p>
 * Addon classes are named by string for the same reason {@link GTMachinePresets} keys are: an addon
 * that is not installed then costs nothing instead of throwing NoClassDefFoundError. A table that
 * cannot be read comes back null and drops its machine out of the preset table, which surfaces as
 * the uncovered-multiblock warning - louder than quietly reporting numbers from a version nobody is
 * running.
 */
public final class GTStructureTiers {

    private GTStructureTiers() {}

    /** GT counts None and ULV below Cupronickel, which is why its {@code getTier()} subtracts two. */
    public static final int MAX_COIL_TIER = HeatingCoilLevel.getMaxTier();

    /** Solenoid tiers are the block meta + 2, so MV is the weakest that exists. */
    public static final int MIN_SOLENOID_TIER = 2;
    /** Read off a registered Block instance by {@code BlockCyclotronCoils.getVoltageTier(meta)}. */
    public static final int MAX_SOLENOID_TIER = 12;
    /** GT computes meta + 1 inside the private {@code GTStructureUtility.getItemPipeCasingTier}. */
    public static final int MAX_ITEM_PIPE_TIER = 8;
    /** Comes from GT++'s private {@code MTEChemicalPlant.mTieredBlockRegistry}, filled at mod init. */
    public static final int MAX_PIPE_CASING_TIER = 4;
    /** Extra Coke Oven slices; also the Dangote tower's height term. Structure shape, not a tier. */
    public static final int MAX_WIDTH = 15;

    /**
     * kubatech's ArcFurnaceElectrode, in declaration order, which is its id order. The Industrial Arc
     * Furnace reads every one of these off the electrode it is holding.
     */
    public record Electrodes(double[] durationModifier, int[] parallel, double[] durationDecreasePerOC,
        double[] eutIncreasePerOC, double[] euModifier) {}

    /** The sawblade in the Industrial Cutting Machine's controller slot, weakest first. */
    public record Sawblades(int[] parallelPerVoltageTier, double[] durationModifier, double[] euModifier) {}

    @Nullable
    public static final Electrodes ELECTRODES = readElectrodes();
    @Nullable
    public static final Sawblades SAWBLADES = readSawblades();

    public static final int MAX_ELECTRODE_TIER = ELECTRODES == null ? 13 : ELECTRODES.parallel().length - 1;
    public static final int MAX_SAWBLADE_TIER = SAWBLADES == null ? 3 : SAWBLADES.durationModifier().length - 1;

    /** Clamps to the table, so a stored tier from a pack with more electrodes still resolves. */
    public static double at(@Nonnull final double[] table, final int tier) {
        return table[Math.max(0, Math.min(table.length - 1, tier))];
    }

    public static int at(@Nonnull final int[] table, final int tier) {
        return table[Math.max(0, Math.min(table.length - 1, tier))];
    }

    @Nullable
    private static Electrodes readElectrodes() {
        final String owner = "kubatech.loaders.ArcFurnaceElectrode";
        final double[] speed = enumField(owner, "speedModifier");
        final double[] parallel = enumField(owner, "parallelLimit");
        final double[] ocSpeed = enumField(owner, "OCSpeedFactor");
        final double[] ocEut = enumField(owner, "OCPowerFactor");
        final double[] eu = enumField(owner, "amperagePerParallel");
        if (speed == null || parallel == null || ocSpeed == null || ocEut == null || eu == null) return null;
        // GT stores how many times faster the electrode is; the preset wants a duration multiplier.
        final double[] durations = new double[speed.length];
        for (int i = 0; i < speed.length; i++) {
            durations[i] = 1 / speed[i];
        }
        // Infinity lists 0 because its parallel doubles per completed run. A planning tool models the
        // cold start, so floor the table at one rather than at "this machine does nothing".
        return new Electrodes(durations, flooredInts(parallel), ocSpeed, ocEut, eu);
    }

    @Nullable
    private static Sawblades readSawblades() {
        final String owner = "gregtech.common.tileentities.machines.multi.MTEIndustrialCuttingMachine$SawbladeTiers";
        final double[] parallel = enumField(owner, "parallelPerVoltageTier");
        // Already a duration multiplier: the constructor assigns 1F / speedBoost.
        final double[] speed = enumField(owner, "speedBoost");
        final double[] eu = enumField(owner, "euModifier");
        if (parallel == null || speed == null || eu == null) return null;
        return new Sawblades(flooredInts(parallel), speed, eu);
    }

    @Nonnull
    private static int[] flooredInts(@Nonnull final double[] read) {
        final int[] parallel = new int[read.length];
        for (int i = 0; i < read.length; i++) {
            parallel[i] = Math.max(1, (int) read[i]);
        }
        return parallel;
    }

    /**
     * Reads one numeric field off every constant of an enum, in declaration order. The fields are not
     * all public - GT's sawblade parameters are package-private - so this goes through
     * {@code setAccessible} rather than a typed reference.
     */
    @Nullable
    private static double[] enumField(final String className, final String fieldName) {
        try {
            final Class<?> owner = Class.forName(className);
            final Field field = owner.getDeclaredField(fieldName);
            AccessibleObject.setAccessible(new AccessibleObject[] { field }, true);
            final Object[] constants = owner.getEnumConstants();
            final double[] read = new double[constants.length];
            for (int i = 0; i < constants.length; i++) {
                read[i] = field.getDouble(constants[i]);
            }
            return read;
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
            PlanNH.LOG.warn("PlanNH: cannot read {}#{} from GregTech", className, fieldName, e);
            return null;
        }
    }
}
