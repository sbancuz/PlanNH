package com.sbancuz.plannh.data.provider.gregtech.probe;

import java.util.EnumSet;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

/**
 * Finds out which structure knobs a machine actually reads, by moving each one and seeing whether
 * any number changes.
 *
 * <p>
 * A settings row used to appear because somebody wrote the knob down next to the machine. That put
 * rows on nodes that ignore them - the Ore Washing Plant offers a mode its recipe logic never looks
 * at - and it left the knob off machines nobody got around to. Asking the machine instead means a
 * row appears only when moving it moves a number the chart would show.
 *
 * <p>
 * Two readings are compared whole, because {@link ProbeReading} is a record. Any difference in
 * parallel count, speed, power, overclock behaviour or heat counts as the knob mattering.
 */
public final class SensitivityScan {

    private SensitivityScan() {}

    /**
     * The knobs among {@code candidates} that change what the machine reports.
     *
     * <p>
     * Only the two ends of each range are read. A knob whose effect appears in the middle of its
     * range and cancels out at both ends would be missed, which no GregTech machine does today - they
     * scale with a casing tier rather than peak in the middle. The alternative costs a probe per tier
     * per knob per machine, which the settings panel would pay for while it draws.
     */
    @Nonnull
    public static EnumSet<Knob> scan(@Nonnull final StructureState reference, @Nonnull final EnumSet<Knob> candidates,
        final int modeCount, @Nonnull final Function<StructureState, ProbeReading> readings) {
        if (!candidates.contains(Knob.MODE) || modeCount < 2) return scanAt(reference, candidates, readings);

        // A mode picks which machine a multiblock is, so the other knobs have to be judged in each of
        // them. The Mega Distillation Tower scales with its height in distillery mode and ignores it in
        // tower mode, and judging it in tower mode alone would hide the height row. The count comes
        // from the machine rather than from the row, because GT ships three-mode machines.
        final EnumSet<Knob> used = EnumSet.noneOf(Knob.class);
        for (int mode = 0; mode < modeCount; mode++) {
            used.addAll(scanAt(reference.with(Knob.MODE, mode), candidates, readings));
        }
        return used;
    }

    @Nonnull
    private static EnumSet<Knob> scanAt(final StructureState reference, final EnumSet<Knob> candidates,
        final Function<StructureState, ProbeReading> readings) {
        final EnumSet<Knob> used = EnumSet.noneOf(Knob.class);
        for (final Knob knob : candidates) {
            final GTSettings.TierRange range = GTSettings.knobRange(knob);
            if (range.min() >= range.max()) continue;

            final ProbeReading low = readings.apply(reference.with(knob, range.min()));
            final ProbeReading high = readings.apply(reference.with(knob, range.max()));
            if (differ(low, high)) used.add(knob);
        }
        return used;
    }

    /**
     * A machine that declined one end says nothing about the knob, so the knob stays off. Hiding a row
     * that does nothing is cheap to undo. Showing one that does nothing is the problem being fixed.
     */
    private static boolean differ(@Nullable final ProbeReading low, @Nullable final ProbeReading high) {
        return low != null && high != null
            && !low.asShown()
                .equals(high.asShown());
    }
}
