package com.sbancuz.plannh.data.provider.gregtech.probe;

import java.util.EnumSet;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.Settings;
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
     * The knobs among {@code candidates} that change what the machine reports. Only the two ends of
     * each range are read, so a knob whose effect peaks mid-range and cancels at both ends is missed -
     * no GregTech machine does that, they scale with a casing tier.
     */
    @Nonnull
    public static EnumSet<Settings> scan(@Nonnull final StructureState reference,
        @Nonnull final EnumSet<Settings> candidates, final int modeCount,
        @Nonnull final Function<StructureState, ProbeReading> readings) {
        if (!candidates.contains(Settings.GT_MODE) || modeCount < 2) return scanAt(reference, candidates, readings);

        // A mode picks which machine a multiblock is, so the other knobs have to be judged in each of
        // them. The Mega Distillation Tower scales with its height in distillery mode and ignores it in
        // tower mode, and judging it in tower mode alone would hide the height row. The count comes
        // from the machine rather than from a row, because GT ships three-mode machines.
        final EnumSet<Settings> others = EnumSet.copyOf(candidates);
        others.remove(Settings.GT_MODE);

        final EnumSet<Settings> used = EnumSet.noneOf(Settings.class);
        ProbeReading inFirstMode = null;
        for (int mode = 0; mode < modeCount; mode++) {
            final StructureState inMode = reference.with(Settings.GT_MODE, mode);
            used.addAll(scanAt(inMode, others, readings));

            // Mode itself is judged across the same sweep rather than by a pair of ends, or a machine
            // whose first two modes happen to agree would lose its mode row on the strength of them.
            final ProbeReading here = readings.apply(inMode);
            if (inFirstMode == null) inFirstMode = here;
            else if (differ(inFirstMode, here)) used.add(Settings.GT_MODE);
        }
        return used;
    }

    @Nonnull
    private static EnumSet<Settings> scanAt(final StructureState reference, final EnumSet<Settings> candidates,
        final Function<StructureState, ProbeReading> readings) {
        final EnumSet<Settings> used = EnumSet.noneOf(Settings.class);
        for (final Settings knob : candidates) {
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
