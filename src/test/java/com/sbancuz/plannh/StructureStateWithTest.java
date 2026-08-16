package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

/**
 * {@code with} writes one knob into one record component by slot number. That is short to read and
 * easy to get wrong by one, and a wrong slot would not fail to compile - it would silently move a
 * coil onto the solenoid field and quietly corrupt every sensitivity scan. These pin the mapping.
 */
class StructureStateWithTest {

    /** Distinct per component, so a value landing in the wrong slot is visible. */
    private static final StructureState BASE = new StructureState(1, 2, 3, 4, 5, 6, 7, 8, 9, 0);

    private static final int SENTINEL = 99;

    private static int[] components(final StructureState s) {
        return new int[] { s.voltageTier(), s.coilTier(), s.solenoidTier(), s.itemPipeTier(), s.pipeCasingTier(),
            s.sawbladeTier(), s.electrodeTier(), s.structureTier(), s.width(), s.mode() };
    }

    private static int changedSlot(final StructureState changed) {
        final int[] before = components(BASE);
        final int[] after = components(changed);
        int found = -1;
        for (int i = 0; i < before.length; i++) {
            if (before[i] == after[i]) continue;
            assertEquals(-1, found, "more than one component moved, so two knobs share a slot");
            found = i;
        }
        return found;
    }

    @ParameterizedTest
    @EnumSource(Knob.class)
    void eachKnobMovesExactlyOneComponent(final Knob knob) {
        final int slot = changedSlot(BASE.with(knob, SENTINEL));

        assertNotEquals(-1, slot, knob + " moved nothing, so its slot points at a field it already equals");
        assertNotEquals(0, slot, "voltage is not a knob and must never be written by with()");
    }

    /** Two knobs writing one slot would make the scan report whichever ran last. */
    @Test
    void everyKnobOwnsItsOwnComponent() {
        final Set<Integer> slots = new HashSet<>();
        for (final Knob knob : Knob.values()) {
            final int slot = changedSlot(BASE.with(knob, SENTINEL));
            assertEquals(true, slots.add(slot), knob + " writes a component another knob already writes");
        }
        assertEquals(Knob.values().length, slots.size());
    }

    /** Spot-checks against the accessors by name, which is what the slot numbers stand for. */
    @Test
    void theSlotsLandOnTheFieldsTheyAreNamedFor() {
        assertEquals(
            SENTINEL,
            BASE.with(Knob.COIL, SENTINEL)
                .coilTier());
        assertEquals(
            SENTINEL,
            BASE.with(Knob.SOLENOID, SENTINEL)
                .solenoidTier());
        assertEquals(
            SENTINEL,
            BASE.with(Knob.ITEM_PIPE, SENTINEL)
                .itemPipeTier());
        assertEquals(
            SENTINEL,
            BASE.with(Knob.PIPE_CASING, SENTINEL)
                .pipeCasingTier());
        assertEquals(
            SENTINEL,
            BASE.with(Knob.SAWBLADE, SENTINEL)
                .sawbladeTier());
        assertEquals(
            SENTINEL,
            BASE.with(Knob.ELECTRODE, SENTINEL)
                .electrodeTier());
        assertEquals(
            SENTINEL,
            BASE.with(Knob.STRUCTURE_TIER, SENTINEL)
                .structureTier());
        assertEquals(
            SENTINEL,
            BASE.with(Knob.WIDTH, SENTINEL)
                .width());
        assertEquals(
            SENTINEL,
            BASE.with(Knob.MODE, SENTINEL)
                .mode());
    }

    /** The voltage tier is carried through untouched, whichever knob moved. */
    @ParameterizedTest
    @EnumSource(Knob.class)
    void voltageSurvivesEveryKnob(final Knob knob) {
        assertEquals(
            BASE.voltageTier(),
            BASE.with(knob, SENTINEL)
                .voltageTier());
    }
}
