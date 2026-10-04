package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.properties.RecipeProperty;

/**
 * A parallel row defaults to whatever the machine allows, since running a multiblock below its maximum
 * is rarely wanted. A fixed default can't do that: a seeded 1 can't be told apart from a user cap
 * of 1.
 */
class AutoIntSettingTest {

    private static final RecipeContext EMPTY = new RecipeContext(new HashMap<RecipeProperty<?>, Object>());

    /** A machine whose structure allows 256 parallels, both its automatic value and its maximum. */
    private static SettingDef<Integer> parallels() {
        return SettingDef.autoIntDefCapped("parallels", 1, 4096, 1, (ctx, s) -> 256, null);
    }

    @Test
    void anUntouchedSettingReadsAsTheMachineMaximum() {
        assertEquals(256, parallels().effectiveInt(EMPTY, Map.of()));
    }

    /**
     * Only key presence counts: a stored zero is a user-chosen zero. Only absence means "use the
     * machine's value".
     */
    @Test
    void aStoredZeroIsADeliberateZeroNotAnUnsetMarker() {
        assertEquals(0, parallels().effectiveInt(EMPTY, Map.of("parallels", 0)));
    }

    @Test
    void anExplicitLowerCapIsHonoured() {
        assertEquals(16, parallels().effectiveInt(EMPTY, Map.of("parallels", 16)));
    }

    /** Stepping up stops at what the machine can do, not at the field's nominal ceiling. */
    @Test
    void theCeilingIsTheMachineNotTheField() {
        assertEquals(256, parallels().effectiveMax(EMPTY, Map.of()));
        assertEquals(4096, parallels().maxInt, "the nominal bound stays wide for other machines");
    }

    /**
     * An ordinary auto row steps up to its declared ceiling. Its automatic value is the machine's
     * working value, not its maximum, so capping the row there would stop an override from going higher.
     * Amperage, tier skips and the heat rows are such rows.
     */
    @Test
    void anOrdinaryAutoRowKeepsItsDeclaredCeiling() {
        final SettingDef<Integer> amps = SettingDef.autoIntDef("amp", 1, 64, (ctx, s) -> 1, null);

        assertEquals(1, amps.effectiveInt(EMPTY, Map.of()), "it still shows what the machine reports");
        assertEquals(64, amps.effectiveMax(EMPTY, Map.of()), "but it can be stepped past it");
    }

    @Test
    void theDefaultStoresNothing() {
        assertEquals(0, parallels().defaultValue, "0 is the unset marker, so nothing is serialized");
    }

    /** A plain int setting is not auto: its maximum is fixed and its stored value is read as is. */
    @Test
    void aPlainIntSettingIsUnaffected() {
        final SettingDef<Integer> plain = SettingDef.intDef("machines", 1, 1, 4096);

        assertFalse(plain.isAuto());
        assertTrue(parallels().isAuto());
        assertEquals(4096, plain.effectiveMax(EMPTY, Map.of()));
        assertEquals(7, plain.effectiveInt(EMPTY, Map.of("machines", 7)));
    }
}
