package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.sbancuz.plannh.data.Settings;
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

    /**
     * The knobs under test. Settings holds far more than these, so the source is the list
     * StructureState itself claims to have slots for - which is what makes a knob added there
     * without a slot fail here rather than pass unnoticed.
     */
    static final List<Settings> KNOBS = List.copyOf(StructureState.KNOBS);

    static Stream<Settings> knobs() {
        return KNOBS.stream();
    }

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
    @MethodSource("knobs")
    void eachKnobMovesExactlyOneComponent(final Settings knob) {
        final int slot = changedSlot(BASE.with(knob, SENTINEL));

        assertNotEquals(-1, slot, knob + " moved nothing, so its slot points at a field it already equals");
        assertNotEquals(0, slot, "voltage is not a knob and must never be written by with()");
    }

    /** Two knobs writing one slot would make the scan report whichever ran last. */
    @Test
    void everyKnobOwnsItsOwnComponent() {
        final Set<Integer> slots = new HashSet<>();
        for (final Settings knob : KNOBS) {
            final int slot = changedSlot(BASE.with(knob, SENTINEL));
            assertEquals(true, slots.add(slot), knob + " writes a component another knob already writes");
        }
        assertEquals(KNOBS.size(), slots.size());
    }

    /**
     * A setting that is not a structure knob has no slot to write, and the switch can no longer say so
     * at compile time now that it reads from an enum holding every setting PlanNH has. Refusing loudly
     * is what stops it landing on slot 0 and silently rewriting the voltage tier.
     */
    @Test
    void aSettingThatIsNotAKnobIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> BASE.with(Settings.MACHINES, SENTINEL));
    }

    /** Spot-checks against the accessors by name, which is what the slot numbers stand for. */
    @Test
    void theSlotsLandOnTheFieldsTheyAreNamedFor() {
        assertEquals(
            SENTINEL,
            BASE.with(Settings.GT_COIL, SENTINEL)
                .coilTier());
        assertEquals(
            SENTINEL,
            BASE.with(Settings.GT_SOLENOID, SENTINEL)
                .solenoidTier());
        assertEquals(
            SENTINEL,
            BASE.with(Settings.GT_ITEM_PIPE, SENTINEL)
                .itemPipeTier());
        assertEquals(
            SENTINEL,
            BASE.with(Settings.GT_PIPE_CASING, SENTINEL)
                .pipeCasingTier());
        assertEquals(
            SENTINEL,
            BASE.with(Settings.GT_SAWBLADE, SENTINEL)
                .sawbladeTier());
        assertEquals(
            SENTINEL,
            BASE.with(Settings.GT_ELECTRODE, SENTINEL)
                .electrodeTier());
        assertEquals(
            SENTINEL,
            BASE.with(Settings.GT_STRUCTURE_TIER, SENTINEL)
                .structureTier());
        assertEquals(
            SENTINEL,
            BASE.with(Settings.GT_WIDTH, SENTINEL)
                .width());
        assertEquals(
            SENTINEL,
            BASE.with(Settings.GT_MODE, SENTINEL)
                .mode());
    }

    /** The voltage tier is carried through untouched, whichever knob moved. */
    @ParameterizedTest
    @MethodSource("knobs")
    void voltageSurvivesEveryKnob(final Settings knob) {
        assertEquals(
            BASE.voltageTier(),
            BASE.with(knob, SENTINEL)
                .voltageTier());
    }
}
