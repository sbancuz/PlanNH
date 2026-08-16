package com.sbancuz.plannh.data.provider.gregtech.probe;

import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.COIL;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.ELECTRODE;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.ITEM_PIPE;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.MODE;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.PIPE_CASING;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.SAWBLADE;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.SOLENOID;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.STRUCTURE_TIER;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.WIDTH;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob;
import com.sbancuz.plannh.data.provider.gregtech.GTStructureTiers;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.ItemList;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * Writes the structure a player would have built into the instance fields the machine reads.
 *
 * <p>
 * A machine's own {@code checkMachine} scans the blocks around it and reduces them to a handful of
 * fields - a coil level, a pipe casing tier - which its processing logic then does arithmetic on. The
 * probe cannot run that scan without a world, but it does not need to: the fields are the whole
 * interface between the structure and the arithmetic, so writing them directly asks the machine the
 * same question a built one would answer.
 *
 * <p>
 * What that costs is a way to recognise the fields. Type is enough for the two that carry a GregTech
 * type of their own, and covers every name GT uses for a coil - {@code coilLevel}, {@code heatLevel},
 * {@code mCoilLevel}, {@code coilHeat}, {@code mHeatingCapacity} - without naming any of them. The
 * rest are plain ints, so they need {@link #BY_NAME}. That list is shared across all machines rather
 * than written per machine, and it holds names, never formulas.
 */
public final class FieldInjector {

    /**
     * The knob a field carries, and how the field spells it. GregTech is not consistent about whether
     * a coil is stored as its level, its tier or its tier plus one, and the difference is a factor of
     * two in the answer.
     */
    private enum Coding {

        COIL_LEVEL(COIL),
        COIL_TIER(COIL),
        /** GT++ stores {@code coilTier + 1}, so that an absent coil reads as one rather than none. */
        COIL_TIER_FROM_ONE(COIL),
        /**
         * Kelvin rather than a tier. The machines that keep one derive it in {@code checkMachine}, so
         * the probe has to supply it: writing the coil next to it changes nothing on its own.
         */
        COIL_HEAT(COIL),
        ELECTRODE_ITEM(ELECTRODE),
        ITEM_PIPE_TIER(ITEM_PIPE),
        SOLENOID_TIER(SOLENOID),
        PIPE_CASING_TIER(PIPE_CASING),
        CASING_TIER(STRUCTURE_TIER),
        SLICES(WIDTH),
        MACHINE_MODE(MODE);

        private final Knob knob;

        Coding(final Knob knob) {
            this.knob = knob;
        }
    }

    /**
     * Field names GregTech uses for the knobs it stores as plain numbers. {@code mTier} is deliberately
     * absent: that is the controller's own voltage tier, which the energy hatch supplies instead.
     */
    private static final Map<String, Coding> BY_NAME = Map.ofEntries(
        Map.entry("mCoilTier", Coding.COIL_TIER),
        Map.entry("mLevel", Coding.COIL_TIER_FROM_ONE),
        // Only the int form lands here. Where the same name holds a HeatingCoilLevel, type wins.
        Map.entry("mHeatingCapacity", Coding.COIL_HEAT),
        Map.entry("itemPipeTier", Coding.ITEM_PIPE_TIER),
        Map.entry("solenoidLevel", Coding.SOLENOID_TIER),
        Map.entry("mPipeCasingTier", Coding.PIPE_CASING_TIER),
        Map.entry("tierPipeCasing", Coding.PIPE_CASING_TIER),
        Map.entry("checkPipe", Coding.PIPE_CASING_TIER),
        Map.entry("tier", Coding.CASING_TIER),
        Map.entry("controllerTier", Coding.CASING_TIER),
        Map.entry("structureTier", Coding.CASING_TIER),
        Map.entry("mSolidCasingTier", Coding.CASING_TIER),
        Map.entry("mMachineCasingTier", Coding.CASING_TIER),
        Map.entry("tierMachineCasing", Coding.CASING_TIER),
        Map.entry("width", Coding.SLICES),
        Map.entry("height", Coding.SLICES),
        Map.entry("mHeight", Coding.SLICES),
        Map.entry("machineMode", Coding.MACHINE_MODE));

    private final List<Write> writes;
    private final EnumSet<Knob> knobs;
    private final boolean takesSawblade;

    private record Write(Field field, Coding coding) {}

    private FieldInjector(final List<Write> writes, final EnumSet<Knob> knobs, final boolean takesSawblade) {
        this.writes = writes;
        this.knobs = knobs;
        this.takesSawblade = takesSawblade;
    }

    @Nonnull
    public static FieldInjector forClass(final Class<?> machineClass) {
        final List<Write> found = new ArrayList<>();
        final EnumSet<Knob> reachable = EnumSet.noneOf(Knob.class);
        for (Class<?> c = machineClass; c != null; c = c.getSuperclass()) {
            for (final Field field : c.getDeclaredFields()) {
                final Coding coding = codingOf(field);
                if (coding == null) continue;
                AccessibleObject.setAccessible(new AccessibleObject[] { field }, true);
                found.add(new Write(field, coding));
                reachable.add(coding.knob);
            }
        }
        final boolean sawblade = declaresSawbladeCheck(machineClass);
        if (sawblade) reachable.add(SAWBLADE);
        // Every multiblock inherits the machineMode field, so the field alone would put a mode row on
        // all of them. A machine that really has modes overrides GregTech's own answer to the question.
        if (!declaresModeSwitch(machineClass)) reachable.remove(MODE);
        return new FieldInjector(List.copyOf(found), reachable, sawblade);
    }

    /** True when a subclass of MTEMultiBlockBase answers {@code supportsMachineModeSwitch} for itself. */
    private static boolean declaresModeSwitch(final Class<?> machineClass) {
        for (Class<?> c = machineClass; c != null && c != MTEMultiBlockBase.class; c = c.getSuperclass()) {
            try {
                c.getDeclaredMethod("supportsMachineModeSwitch");
                return true;
            } catch (final NoSuchMethodException keepWalking) {
                // Most machines, which is why the base class declaring it is not enough to go on.
            }
        }
        return false;
    }

    /**
     * The one knob a machine holds as an item rather than as a number: the Industrial Cutting Machine
     * reads its sawblade straight out of the controller slot. Recognised by the machine declaring
     * GregTech's own {@code isValidSawblade} rather than by naming the class.
     */
    private static boolean declaresSawbladeCheck(final Class<?> machineClass) {
        for (Class<?> c = machineClass; c != null; c = c.getSuperclass()) {
            try {
                c.getDeclaredMethod("isValidSawblade", ItemStack.class);
                return true;
            } catch (final NoSuchMethodException keepWalking) {
                // Almost every machine, which is the point of asking.
            }
        }
        return false;
    }

    /** The knobs this machine could possibly read. The sensitivity scan narrows it to those it does. */
    @Nonnull
    public EnumSet<Knob> reachableKnobs() {
        return EnumSet.copyOf(knobs);
    }

    /**
     * Writes the state onto the machine. A field that refuses the write is skipped rather than
     * abandoning the rest: a machine reading four knobs should still answer for the three that took.
     */
    void apply(@Nonnull final MTEMultiBlockBase machine, @Nonnull final StructureState state) {
        for (final Write write : writes) {
            try {
                set(write, machine, state);
            } catch (final ReflectiveOperationException | RuntimeException skip) {
                // Left as the machine's own default, which is what an unprobed knob already means.
            }
        }
        if (takesSawblade) putSawblade(machine, state.sawbladeTier());
    }

    private static void putSawblade(final MTEMultiBlockBase machine, final int tier) {
        try {
            final int slot = machine.getControllerSlotIndex();
            if (slot < machine.mInventory.length) {
                machine.mInventory[slot] = ItemList
                    .valueOf("T" + (clamp(tier, GTStructureTiers.MAX_SAWBLADE_TIER) + 1) + "Sawblade")
                    .get(1);
            }
        } catch (final RuntimeException skip) {
            // No such sawblade in this GregTech; the machine then answers as if the slot were empty.
        }
    }

    private static void set(final Write write, final Object machine, final StructureState state)
        throws ReflectiveOperationException {
        if (write.coding() == Coding.COIL_LEVEL) {
            write.field()
                .set(
                    machine,
                    HeatingCoilLevel.getFromTier((byte) clamp(state.coilTier(), GTStructureTiers.MAX_COIL_TIER)));
            return;
        }
        if (write.coding() == Coding.ELECTRODE_ITEM) {
            write.field()
                .set(machine, electrode(state.electrodeTier()));
            return;
        }
        setNumber(write.field(), machine, number(write.coding(), state));
    }

    private static int number(final Coding coding, final StructureState state) {
        return switch (coding) {
            case COIL_TIER -> state.coilTier();
            case COIL_TIER_FROM_ONE -> state.coilTier() + 1;
            // What the coil alone supplies. A machine that adds a voltage term to this in checkMachine
            // then reads low, which the shadow log reports as a heat difference against the table.
            case COIL_HEAT -> GTStructureTiers.coilHeat(state.coilTier());
            case ITEM_PIPE_TIER -> state.itemPipeTier();
            case SOLENOID_TIER -> state.solenoidTier();
            case PIPE_CASING_TIER -> state.pipeCasingTier();
            case CASING_TIER -> state.structureTier();
            case SLICES -> state.width();
            case MACHINE_MODE -> state.mode();
            default -> 0;
        };
    }

    /** GregTech stores these as int, byte or a boxed Byte depending on the machine. */
    private static void setNumber(final Field field, final Object machine, final int value)
        throws ReflectiveOperationException {
        final Class<?> type = field.getType();
        if (type == int.class) {
            field.setInt(machine, value);
        } else if (type == byte.class) {
            field.setByte(machine, (byte) value);
        } else if (type == Byte.class) {
            field.set(machine, (byte) value);
        } else if (type == Integer.class) {
            field.set(machine, value);
        }
    }

    @Nonnull
    private static Object electrode(final int tier) throws ReflectiveOperationException {
        final Object[] all = Class.forName("kubatech.loaders.ArcFurnaceElectrode")
            .getEnumConstants();
        return all[clamp(tier, all.length - 1)];
    }

    private static int clamp(final int value, final int max) {
        return Math.max(0, Math.min(max, value));
    }

    @Nullable
    private static Coding codingOf(final Field field) {
        final Class<?> type = field.getType();
        if (type == HeatingCoilLevel.class) return Coding.COIL_LEVEL;
        if ("kubatech.loaders.ArcFurnaceElectrode".equals(type.getName())) return Coding.ELECTRODE_ITEM;
        if (type != int.class && type != byte.class && type != Byte.class && type != Integer.class) return null;
        return BY_NAME.get(field.getName());
    }
}
