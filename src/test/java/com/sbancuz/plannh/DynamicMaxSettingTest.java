package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.properties.RecipeProperty;

/**
 * A row whose ceiling the machine sets. Distinct from an auto row, where the machine's number is also
 * what the row shows: here the row keeps its own default and only the top of the range moves, which is
 * what a machine mode needs - mode 0 is a real choice, and how many modes exist is the machine's.
 */
class DynamicMaxSettingTest {

    private static final RecipeContext EMPTY = new RecipeContext(Map.<RecipeProperty<?>, Object>of());

    @Test
    void theCeilingComesFromTheFunctionNotTheDeclaration() {
        final SettingDef<Integer> def = SettingDef.intDef("gt_mode", 0, 0, (ctx, s) -> 2);

        assertEquals(2, def.effectiveMax(EMPTY, Map.of()), "a three-mode machine must reach mode 2");
    }

    /** Unset still reads as the default, because the ceiling is not an automatic value. */
    @Test
    void anUntouchedRowStillReadsAsItsDefault() {
        final SettingDef<Integer> def = SettingDef.intDef("gt_mode", 0, 0, (ctx, s) -> 2);

        assertEquals(0, def.effectiveInt(EMPTY, Map.of()));
    }

    /** A machine that reports fewer modes than the row's floor must not invert the range. */
    @Test
    void theCeilingNeverFallsBelowTheFloor() {
        final SettingDef<Integer> def = SettingDef.intDef("gt_mode", 0, 0, (ctx, s) -> -5);

        assertEquals(0, def.effectiveMax(EMPTY, Map.of()));
    }

    /**
     * The trap this exists to prevent: a machine-set ceiling is NOT an auto row, so a caller that asks
     * {@code isAuto()} before consulting the ceiling gets the declared maximum - which for such a row is
     * its own minimum, leaving the row unsteppable. Every caller must ask effectiveMax unconditionally.
     */
    @Test
    void aCeilingRowIsNotAnAutoRowButStillHasACeiling() {
        final SettingDef<Integer> def = SettingDef.intDef("gt_mode", 0, 0, (ctx, s) -> 2);

        assertFalse(def.isAuto(), "gating on isAuto is what made the mode row unsteppable");
        assertEquals(2, def.effectiveMax(EMPTY, Map.of()));
    }

    /** Every other row is unaffected: a plain int def still reports the maximum it declared. */
    @Test
    void aPlainIntRowIsUnchanged() {
        final SettingDef<Integer> def = SettingDef.intDef("gt_width", 15, 0, 15);

        assertEquals(15, def.effectiveMax(EMPTY, Map.of()));
    }
}
