package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.properties.RecipeProperty;

/**
 * Derived rows draw what minimisation changed, so a row whose value means the machine did nothing
 * is noise and is suppressed.
 */
class DerivedRowTest {

    private static final RecipeContext EMPTY = new RecipeContext(Map.<RecipeProperty<?>, Object>of());

    @Test
    void aRowReportingItsNeutralSaysNothing() {
        final SettingDef<Integer> discount = SettingDef
            .autoIntDef("eut_discount", 0, 100, 100, (ctx, s) -> 100, (v, c) -> "D" + v);

        assertTrue(discount.isNeutral(discount.effectiveInt(EMPTY, Map.of())), "100% is no discount at all");
        assertFalse(discount.isNeutral(90), "an actual discount must still be reported");
    }

    /** GregTech's default for a machine that never sets it is 1, not the field's declared 0. */
    @Test
    void theNeutralIsTheMachinesNotTheFields() {
        final SettingDef<Integer> skips = SettingDef
            .autoIntDef("max_tier_skips", 0, 10, 1, (ctx, s) -> 1, (v, c) -> "Sk" + v);

        assertTrue(skips.isNeutral(1));
        assertFalse(skips.isNeutral(0), "a machine that forbids skipping said something");
    }

    /** A row without a declared neutral suppresses no value. */
    @Test
    void anUndeclaredNeutralNeverSuppresses() {
        final SettingDef<Integer> plain = SettingDef.autoIntDef("amp", 1, 64, (ctx, s) -> 1, null);

        assertFalse(plain.isNeutral(1));
    }
}
