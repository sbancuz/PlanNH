package com.sbancuz.plannh.data.provider.gregtech;

import java.lang.reflect.Field;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.Reflect;

import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.ItemList;

/**
 * How far each structure setting goes, and the per-tier numbers GregTech attaches to the two settings that
 * carry their own parameter tables.
 *
 * <p>
 * Everything GregTech exposes is read from GregTech, so a pack running a different GT version gets
 * that version's numbers rather than the ones a PlanNH release was written against. The rest is
 * declared here because GT keeps it in instance methods on registered blocks or in private
 * registries filled at mod init, neither of which a class can read.
 *
 * <p>
 * Addon classes are named by string for the same reason {@link GTMachineOverrides} keys are: an addon
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
     * The pipe casings, weakest first, so tier 1 is Bronze. Both machines that read the setting agree on
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

    /** Ceilings for a pack whose tables would not read; the row then bounds a setting no machine offers. */
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

    /** Into {@code [0, max]}. A stored tier from a pack with a longer table still resolves to a real one. */
    public static int clamp(final int tier, final int max) {
        return Math.max(0, Math.min(max, tier));
    }

    public static int clampCoil(final int coilTier) {
        return clamp(coilTier, MAX_COIL_TIER);
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

    /**
     * kubatech's own electrode enum, named once. The machine probe writes one of these constants into
     * the Industrial Arc Furnace and recognises the field by this type, so a second spelling of the
     * name would be a second answer to what an electrode is.
     */
    public static final String ELECTRODE_CLASS = "kubatech.loaders.ArcFurnaceElectrode";

    /** Held rather than re-read: {@code getEnumConstants} hands back a fresh copy on every call. */
    @Nullable
    private static final Object[] ELECTRODE_CONSTANTS = electrodeConstants();

    @Nullable
    private static Object[] electrodeConstants() {
        final Class<?> owner = Reflect.type(ELECTRODE_CLASS);
        final Object[] all = owner == null ? null : owner.getEnumConstants();
        return all == null || all.length == 0 ? null : all;
    }

    /**
     * One electrode, for the probe, which hands the machine a constant rather than a tier. Null on a
     * pack without kubatech, where the machine keeps whatever it was built holding.
     */
    @Nullable
    public static Object electrode(final int tier) {
        if (ELECTRODE_CONSTANTS == null) return null;
        return ELECTRODE_CONSTANTS[clamp(tier, ELECTRODE_CONSTANTS.length - 1)];
    }

    @Nullable
    private static Electrodes readElectrodes() {
        final String owner = ELECTRODE_CLASS;
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
     * {@link Reflect} rather than a typed reference.
     *
     * <p>
     * A missing class and a missing field are not the same news. kubatech is optional, so a pack
     * without it is expected and says nothing; a class that is present but has lost a field means the
     * mod moved under us, and that is worth a line in the log.
     */
    @Nullable
    private static double[] enumField(final String className, final String fieldName) {
        final Class<?> owner = Reflect.type(className);
        if (owner == null) return null;
        try {
            final Field field = Reflect.field(owner, fieldName);
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
