package com.sbancuz.plannh.data.provider.gregtech;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.sbancuz.plannh.PlanNH;

import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.ItemList;

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

    /**
     * The pipe casings, weakest first, so tier 1 is Bronze. Both machines that read the knob agree on
     * this order: GT++'s Chemical Plant takes block meta 12 to 15 as tier 1 to 4, and GregTech's steam
     * multiblocks take the same two lowest metas as their tier 1 and 2.
     */
    private static final ItemList[] PIPE_CASINGS = { ItemList.Casing_Pipe_Bronze, ItemList.Casing_Pipe_Steel,
        ItemList.Casing_Pipe_Titanium, ItemList.Casing_Pipe_TungstenSteel };

    public static final int MAX_PIPE_CASING_TIER = PIPE_CASINGS.length;

    /** Filled on first use, because item display names need a registry that is empty at class load. */
    private static final String[] PIPE_CASING_NAMES = new String[PIPE_CASINGS.length];

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

    /** Ceilings for a pack whose tables would not read; the row then bounds a knob no machine offers. */
    private static final int ELECTRODE_TIERS_AT_5_09_52 = 13;
    private static final int SAWBLADE_TIERS_AT_5_09_52 = 3;

    public static final int MAX_ELECTRODE_TIER = ELECTRODES == null ? ELECTRODE_TIERS_AT_5_09_52
        : ELECTRODES.parallel().length - 1;
    public static final int MAX_SAWBLADE_TIER = SAWBLADES == null ? SAWBLADE_TIERS_AT_5_09_52
        : SAWBLADES.durationModifier().length - 1;

    /** How hot a coil of this tier runs, in Kelvin. Tiers outside the range clamp to it. */
    public static int coilHeat(final int coilTier) {
        return (int) HeatingCoilLevel.getFromTier((byte) clampCoil(coilTier))
            .getHeat();
    }

    public static int clampCoil(final int coilTier) {
        return Math.max(0, Math.min(MAX_COIL_TIER, coilTier));
    }

    /**
     * GregTech's own name for the casing at a pipe casing tier, so the row names the block a player
     * places rather than a number only the code uses. Falls back to the tier when the item registry
     * has nothing, which is what a headless run sees.
     */
    @Nonnull
    public static String pipeCasingName(final int tier) {
        final int index = Math.max(1, Math.min(MAX_PIPE_CASING_TIER, tier)) - 1;
        if (PIPE_CASING_NAMES[index] == null) {
            PIPE_CASING_NAMES[index] = readItemName(PIPE_CASINGS[index], String.valueOf(index + 1));
        }
        return PIPE_CASING_NAMES[index];
    }

    /** The casing kind, which every row that shows one of these has already said in its own label. */
    private static final String PIPE_CASING_SUFFIX = " Pipe Casing";

    /**
     * The material rather than the whole item name, so a row reads "Pipe Casing Tungstensteel" the way
     * a coil row reads "Coil HSS-S" - GregTech names a coil by its material already, and names a
     * casing by material and kind together. A locale that words it differently keeps the full name,
     * which is long but never wrong.
     */
    @Nonnull
    private static String readItemName(final ItemList item, final String fallback) {
        try {
            final ItemStack stack = item.get(1);
            if (stack == null) return fallback;
            final String name = stack.getDisplayName();
            return name.endsWith(PIPE_CASING_SUFFIX) ? name.substring(0, name.length() - PIPE_CASING_SUFFIX.length())
                : name;
        } catch (final RuntimeException | LinkageError e) {
            return fallback;
        }
    }

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
