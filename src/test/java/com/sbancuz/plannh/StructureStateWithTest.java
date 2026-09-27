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
 * {@code with} writes one setting into one record component by slot number. That is short to read and
 * easy to get wrong by one, and a wrong slot would not fail to compile - it would silently move a
 * coil onto the solenoid field and quietly corrupt every sensitivity scan. These pin the mapping.
 */
class StructureStateWithTest {

    /** Distinct per component, so a value landing in the wrong slot is visible. */
    private static final StructureState BASE = new StructureState(1, 2, 3, 4, 5, 6, 7, 8, 9, 0);

    private static final int SENTINEL = 99;

    /**
     * The settings under test. Settings holds far more than these, so the source is the list
     * StructureState itself claims to have slots for - which is what makes a setting added there
     * without a slot fail here rather than pass unnoticed.
     */
    static final List<Settings> STRUCTURE_SETTINGS = List.copyOf(StructureState.STRUCTURE_SETTINGS);

    static Stream<Settings> settings() {
        return STRUCTURE_SETTINGS.stream();
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
            assertEquals(-1, found, "more than one component moved, so two settings share a slot");
            found = i;
        }
        return found;
    }

    @ParameterizedTest
    @MethodSource("settings")
    void eachSettingMovesExactlyOneComponent(final Settings setting) {
        final int slot = changedSlot(BASE.with(setting, SENTINEL));

        assertNotEquals(-1, slot, setting + " moved nothing, so its slot points at a field it already equals");
        assertNotEquals(0, slot, "voltage is not a setting and must never be written by with()");
    }

    /** Two settings writing one slot would make the scan report whichever ran last. */
    @Test
    void everySettingOwnsItsOwnComponent() {
        final Set<Integer> slots = new HashSet<>();
        for (final Settings setting : STRUCTURE_SETTINGS) {
            final int slot = changedSlot(BASE.with(setting, SENTINEL));
            assertEquals(true, slots.add(slot), setting + " writes a component another setting already writes");
        }
        assertEquals(STRUCTURE_SETTINGS.size(), slots.size());
    }

    /**
     * A setting that is not a structure setting has no slot to write, and the switch can no longer say so
     * at compile time now that it reads from an enum holding every setting PlanNH has. Refusing loudly
     * is what stops it landing on slot 0 and silently rewriting the voltage tier.
     */
    @Test
    void aSettingWithNoComponentIsRefused() {
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

    /** The voltage tier is carried through untouched, whichever setting moved. */
    @ParameterizedTest
    @MethodSource("settings")
    void voltageSurvivesEverySetting(final Settings setting) {
        assertEquals(
            BASE.voltageTier(),
            BASE.with(setting, SENTINEL)
                .voltageTier());
    }
}
