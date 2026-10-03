package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.properties.RecipeProperty;

/**
 * A row whose ceiling the machine sets. Unlike an auto row, it starts at its default, not at the
 * machine's number, and only the top of the range moves. Machine modes use this: mode 0 is a real
 * choice, and the machine sets how many modes exist.
 */
class DynamicMaxSettingTest {

    private static final RecipeContext EMPTY = new RecipeContext(Map.<RecipeProperty<?>, Object>of());

    @Test
    void theCeilingComesFromTheFunctionNotTheDeclaration() {
        final SettingDef<Integer> def = SettingDef.intDef("gt_mode", 0, 0, (ctx, s) -> 2);

        assertEquals(2, def.effectiveMax(EMPTY, Map.of()), "a three-mode machine must reach mode 2");
    }

    /** Unset resolves to the default, because the ceiling is not an automatic value. */
    @Test
    void anUntouchedRowStillReadsAsItsDefault() {
        final SettingDef<Integer> def = SettingDef.intDef("gt_mode", 0, 0, (ctx, s) -> 2);

        assertEquals(0, def.effectiveInt(EMPTY, Map.of()));
    }

    /** A machine with fewer modes than the row's floor must not invert the range. */
    @Test
    void theCeilingNeverFallsBelowTheFloor() {
        final SettingDef<Integer> def = SettingDef.intDef("gt_mode", 0, 0, (ctx, s) -> -5);

        assertEquals(0, def.effectiveMax(EMPTY, Map.of()));
    }

    /**
     * The trap this guards: a machine-set ceiling is NOT an auto row, so a caller that checks
     * {@code isAuto()} before reading the ceiling gets the declared maximum. For such a row that equals
     * its minimum, which leaves the row unsteppable. Every caller must call effectiveMax unconditionally.
     */
    @Test
    void aCeilingRowIsNotAnAutoRowButStillHasACeiling() {
        final SettingDef<Integer> def = SettingDef.intDef("gt_mode", 0, 0, (ctx, s) -> 2);

        assertFalse(def.isAuto(), "gating on isAuto is what made the mode row unsteppable");
        assertEquals(2, def.effectiveMax(EMPTY, Map.of()));
    }

    /** Every other row is unaffected: a plain int def still returns its declared maximum. */
    @Test
    void aPlainIntRowIsUnchanged() {
        final SettingDef<Integer> def = SettingDef.intDef("gt_width", 15, 0, 15);

        assertEquals(15, def.effectiveMax(EMPTY, Map.of()));
    }
}
