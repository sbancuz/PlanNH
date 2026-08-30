package com.sbancuz.plannh.data.provider.gregtech.probe;

import static com.sbancuz.plannh.data.Settings.GT_COIL;
import static com.sbancuz.plannh.data.Settings.GT_ELECTRODE;
import static com.sbancuz.plannh.data.Settings.GT_ITEM_PIPE;
import static com.sbancuz.plannh.data.Settings.GT_MODE;
import static com.sbancuz.plannh.data.Settings.GT_PIPE_CASING;
import static com.sbancuz.plannh.data.Settings.GT_SAWBLADE;
import static com.sbancuz.plannh.data.Settings.GT_SOLENOID;
import static com.sbancuz.plannh.data.Settings.GT_STRUCTURE_TIER;
import static com.sbancuz.plannh.data.Settings.GT_WIDTH;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.sbancuz.plannh.data.Reflect;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.provider.gregtech.GTStructureTiers;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.ItemList;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * Writes the structure a player would have built into the instance fields the machine reads. The
 * other half of the probe, {@link OverclockInternals}, goes the other way: it reads GregTech's
 * arithmetic back out once this has posed the question.
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
public final class StructureWriter {

    /**
     * The setting a field carries, and how the field spells it. GregTech is not consistent about whether
     * a coil is stored as its level, its tier or its tier plus one, and the difference is a factor of
     * two in the answer.
     */
    private enum Coding {

        COIL_LEVEL(GT_COIL),
        COIL_TIER(GT_COIL),
        /** GT++ stores {@code coilTier + 1}, so that an absent coil reads as one rather than none. */
        COIL_TIER_FROM_ONE(GT_COIL),
        /**
         * Kelvin rather than a tier. The machines that keep one derive it in {@code checkMachine}, so
         * the probe has to supply it: writing the coil next to it changes nothing on its own.
         */
        COIL_HEAT(GT_COIL),
        ELECTRODE_ITEM(GT_ELECTRODE),
        ITEM_PIPE_TIER(GT_ITEM_PIPE),
        SOLENOID_TIER(GT_SOLENOID),
        PIPE_CASING_TIER(GT_PIPE_CASING),
        CASING_TIER(GT_STRUCTURE_TIER),
        SLICES(GT_WIDTH),
        MACHINE_MODE(GT_MODE);

        private final Settings setting;

        Coding(final Settings setting) {
            this.setting = setting;
        }
    }

    /**
     * Field names GregTech uses for the settings it stores as plain numbers. {@code mTier} is deliberately
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
    private final EnumSet<Settings> settings;
    private final boolean takesSawblade;

    private record Write(Field field, Coding coding) {}

    private StructureWriter(final List<Write> writes, final EnumSet<Settings> settings, final boolean takesSawblade) {
        this.writes = writes;
        this.settings = settings;
        this.takesSawblade = takesSawblade;
    }

    @Nonnull
    public static StructureWriter forClass(final Class<?> machineClass) {
        final List<Write> found = new ArrayList<>();
        final EnumSet<Settings> reachable = EnumSet.noneOf(Settings.class);
        for (Class<?> c = machineClass; c != null; c = c.getSuperclass()) {
            for (final Field field : c.getDeclaredFields()) {
                final Coding coding = codingOf(field);
                if (coding == null) continue;
                found.add(new Write(Reflect.accessible(field), coding));
                reachable.add(coding.setting);
            }
        }
        final boolean sawblade = declaresSawbladeCheck(machineClass);
        if (sawblade) reachable.add(GT_SAWBLADE);
        // Every multiblock inherits the machineMode field, so the field alone would put a mode row on
        // all of them. A machine that really has modes overrides GregTech's own answer to the question.
        if (!declaresModeSwitch(machineClass)) reachable.remove(GT_MODE);
        return new StructureWriter(List.copyOf(found), reachable, sawblade);
    }

    /**
     * True when a subclass of MTEMultiBlockBase answers {@code supportsMachineModeSwitch} for itself.
     * The walk stops at the base class, which declares it for every machine and so says nothing.
     */
    private static boolean declaresModeSwitch(final Class<?> machineClass) {
        return Reflect.declaredMethod(machineClass, MTEMultiBlockBase.class, "supportsMachineModeSwitch") != null;
    }

    /**
     * The one setting a machine holds as an item rather than as a number: the Industrial Cutting Machine
     * reads its sawblade straight out of the controller slot. Recognised by the machine declaring
     * GregTech's own {@code isValidSawblade} rather than by naming the class.
     */
    private static boolean declaresSawbladeCheck(final Class<?> machineClass) {
        return Reflect.declaredMethod(machineClass, null, "isValidSawblade", ItemStack.class) != null;
    }

    /** The settings this machine could possibly read. The sensitivity scan narrows it to those it does. */
    @Nonnull
    public EnumSet<Settings> reachableSettings() {
        return EnumSet.copyOf(settings);
    }

    /**
     * Writes the state onto the machine. A field that refuses the write is skipped rather than
     * abandoning the rest: a machine reading four settings should still answer for the three that took.
     */
    void apply(@Nonnull final MTEMultiBlockBase machine, @Nonnull final StructureState state) {
        for (final Write write : writes) {
            try {
                set(write, machine, state);
            } catch (final ReflectiveOperationException | RuntimeException skip) {
                // Left as the machine's own default, which is what an unprobed setting already means.
            }
        }
        if (takesSawblade) putSawblade(machine, state.sawbladeTier());
    }

    private static void putSawblade(final MTEMultiBlockBase machine, final int tier) {
        try {
            final int slot = machine.getControllerSlotIndex();
            if (slot < machine.mInventory.length) {
                machine.mInventory[slot] = ItemList
                    .valueOf("T" + (GTStructureTiers.clamp(tier, GTStructureTiers.MAX_SAWBLADE_TIER) + 1) + "Sawblade")
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
                .set(machine, HeatingCoilLevel.getFromTier((byte) GTStructureTiers.clampCoil(state.coilTier())));
            return;
        }
        if (write.coding() == Coding.ELECTRODE_ITEM) {
            // Left alone rather than nulled when kubatech has no electrode to give: the machine's own
            // default is a state it can survive, and null is one it was never written to expect.
            final Object electrode = GTStructureTiers.electrode(state.electrodeTier());
            if (electrode != null) {
                write.field()
                    .set(machine, electrode);
            }
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

    @Nullable
    private static Coding codingOf(final Field field) {
        final Class<?> type = field.getType();
        if (type == HeatingCoilLevel.class) return Coding.COIL_LEVEL;
        if (GTStructureTiers.ELECTRODE_CLASS.equals(type.getName())) return Coding.ELECTRODE_ITEM;
        if (type != int.class && type != byte.class && type != Byte.class && type != Integer.class) return null;
        return BY_NAME.get(field.getName());
    }
}
