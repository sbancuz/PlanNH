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
 * Parallels should sit at whatever the machine allows, because running a multiblock below its
 * maximum is almost never wanted. A fixed default cannot express that: a seeded 1 is indistinguishable
 * from a deliberate cap of 1, which is what silently ran a 256-parallel Mega Chemical Reactor at one.
 */
class AutoIntSettingTest {

    private static final RecipeContext EMPTY = new RecipeContext(new HashMap<RecipeProperty<?>, Object>());

    /** Stands in for a machine whose structure allows 256. */
    private static SettingDef<Integer> parallels() {
        return SettingDef.autoIntDef("parallels", 1, 4096, (ctx, s) -> 256, null);
    }

    @Test
    void anUntouchedSettingReadsAsTheMachineMaximum() {
        assertEquals(256, parallels().effectiveInt(EMPTY, Map.of()));
    }

    /**
     * Presence is the whole test now: a stored zero is a deliberate zero, which is what the old
     * sentinel could not say. Absence is the only thing that means "ask the machine".
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

    @Test
    void theDefaultStoresNothing() {
        assertEquals(0, parallels().defaultValue, "0 is the unset marker, so nothing is serialized");
    }

    /** A plain int setting must keep behaving exactly as before. */
    @Test
    void aPlainIntSettingIsUnaffected() {
        final SettingDef<Integer> plain = SettingDef.intDef("machines", 1, 1, 4096);

        assertFalse(plain.isAuto());
        assertTrue(parallels().isAuto());
        assertEquals(4096, plain.effectiveMax(EMPTY, Map.of()));
        assertEquals(7, plain.effectiveInt(EMPTY, Map.of("machines", 7)));
    }
}
