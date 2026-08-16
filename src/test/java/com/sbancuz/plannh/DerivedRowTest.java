package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.properties.RecipeProperty;

/**
 * The derived rows exist to show that minimisation worked, so a row saying the machine did nothing is
 * worse than no row: it is the noise the minimisation was supposed to remove.
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

    /** GregTech's own default when a machine never asked is one, not the zero the field declares. */
    @Test
    void theNeutralIsTheMachinesNotTheFields() {
        final SettingDef<Integer> skips = SettingDef
            .autoIntDef("max_tier_skips", 0, 10, 1, (ctx, s) -> 1, (v, c) -> "Sk" + v);

        assertTrue(skips.isNeutral(1));
        assertFalse(skips.isNeutral(0), "a machine that forbids skipping said something");
    }

    /** A row with no declared neutral reports every value, rather than guessing one. */
    @Test
    void anUndeclaredNeutralNeverSuppresses() {
        final SettingDef<Integer> plain = SettingDef.autoIntDef("amp", 1, 64, (ctx, s) -> 1, null);

        assertFalse(plain.isNeutral(1));
    }
}
