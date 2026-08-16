package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;
import com.sbancuz.plannh.data.provider.gregtech.probe.ProbeReading;
import com.sbancuz.plannh.data.provider.gregtech.probe.SensitivityScan;

/**
 * The scan decides which settings rows a node shows, so its rule has to be exact: a knob counts only
 * when moving it moves a number. A stub machine stands in for GregTech here, which lets the rule be
 * tested against a known answer rather than against whatever the pack happens to ship.
 */
class GTSensitivityScanTest {

    private static final StructureState REFERENCE = new StructureState(1, 5, 6, 4, 2, 1, 0, 1, 4, 0);

    /** Two, the usual machine. A stub has no GregTech to ask. */
    private static final int MODES = 2;

    /** Everything at the calculator's defaults. Individual tests vary one number from this. */
    private static ProbeReading flat() {
        return new ProbeReading(1, 1.0, 1.0, 4.0, 2.0, 1, false, false, 0, 0, 100, 100, false, false);
    }

    private static ProbeReading withParallel(final int parallel) {
        return new ProbeReading(parallel, 1.0, 1.0, 4.0, 2.0, 1, false, false, 0, 0, 100, 100, false, false);
    }

    private static ProbeReading withMachineHeat(final int heat) {
        return new ProbeReading(1, 1.0, 1.0, 4.0, 2.0, 1, true, false, heat, 0, 100, 100, false, false);
    }

    @Test
    void aKnobThatChangesNothingIsNotUsed() {
        final EnumSet<Knob> used = SensitivityScan
            .scan(REFERENCE, EnumSet.of(Knob.COIL, Knob.MODE, Knob.WIDTH), MODES, state -> flat());

        assertTrue(used.isEmpty(), "no number moved, so no row belongs on the node");
    }

    @Test
    void aKnobThatChangesTheParallelCountIsUsed() {
        final EnumSet<Knob> used = SensitivityScan
            .scan(REFERENCE, EnumSet.of(Knob.COIL, Knob.MODE), MODES, state -> withParallel(1 + state.coilTier()));

        assertEquals(EnumSet.of(Knob.COIL), used);
    }

    /** Heat is as much a chart number as speed is, so a coil that only changes heat still counts. */
    @Test
    void aKnobThatOnlyChangesHeatIsUsed() {
        final EnumSet<Knob> used = SensitivityScan
            .scan(REFERENCE, EnumSet.of(Knob.COIL), MODES, state -> withMachineHeat(1800 + 900 * state.coilTier()));

        assertEquals(EnumSet.of(Knob.COIL), used);
    }

    /** Two knobs, one machine: only the one that is read comes back. */
    @Test
    void knobsAreJudgedOneAtATime() {
        final Function<StructureState, ProbeReading> readings = state -> withParallel(2 * state.pipeCasingTier());
        final EnumSet<Knob> used = SensitivityScan
            .scan(REFERENCE, EnumSet.of(Knob.COIL, Knob.PIPE_CASING, Knob.SOLENOID), MODES, readings);

        assertEquals(EnumSet.of(Knob.PIPE_CASING), used);
    }

    /** A knob outside the candidate set is never asked about, however much the machine reads it. */
    @Test
    void onlyCandidatesAreScanned() {
        final EnumSet<Knob> used = SensitivityScan
            .scan(REFERENCE, EnumSet.of(Knob.WIDTH), MODES, state -> withParallel(1 + state.coilTier()));

        assertTrue(used.isEmpty());
    }

    /** A machine that declines an end says nothing, so the row stays off rather than guessing. */
    @Test
    void aDeclinedReadingLeavesTheKnobOff() {
        final EnumSet<Knob> used = SensitivityScan.scan(
            REFERENCE,
            EnumSet.of(Knob.COIL),
            MODES,
            state -> state.coilTier() == 0 ? null : withParallel(1 + state.coilTier()));

        assertFalse(used.contains(Knob.COIL));
    }

    /** A machine with a third mode must have that mode examined, not just the first two. */
    @Test
    void everyModeTheMachineHasIsScanned() {
        // Only readable in mode 2, which a two-mode sweep would never visit.
        final Function<StructureState, ProbeReading> readings = state -> state.mode() == 2
            ? withParallel(1 + state.coilTier())
            : flat();

        assertFalse(
            SensitivityScan.scan(REFERENCE, EnumSet.of(Knob.COIL, Knob.MODE), 2, readings)
                .contains(Knob.COIL),
            "a two-mode sweep cannot see it");
        assertTrue(
            SensitivityScan.scan(REFERENCE, EnumSet.of(Knob.COIL, Knob.MODE), 3, readings)
                .contains(Knob.COIL),
            "a three-mode machine must have its third mode scanned");
    }

    /**
     * A three-mode machine whose first two modes agree still needs its mode row: judging MODE on a pair
     * of ends would take those two as the whole answer and hide the third.
     */
    @Test
    void aThirdModeThatDiffersEarnsTheModeRow() {
        final Function<StructureState, ProbeReading> readings = state -> state.mode() == 2 ? withParallel(9) : flat();

        assertTrue(
            SensitivityScan.scan(REFERENCE, EnumSet.of(Knob.MODE), 3, readings)
                .contains(Knob.MODE),
            "mode 2 differs, so the mode row belongs on the node");
        assertFalse(
            SensitivityScan.scan(REFERENCE, EnumSet.of(Knob.MODE), 2, readings)
                .contains(Knob.MODE),
            "with only two modes nothing differs, so no row");
    }

    /** The scan must cover what the row offers, or a knob could move outside the range it was tested on. */
    @Test
    void everyKnobRangeIsRealAndNonEmpty() {
        for (final Knob knob : Knob.values()) {
            final GTSettings.TierRange range = GTSettings.knobRange(knob);
            assertTrue(range.min() < range.max(), knob + " offers nothing to scan: " + range);
        }
    }
}
