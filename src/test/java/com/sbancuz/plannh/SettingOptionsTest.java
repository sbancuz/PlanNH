package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.properties.RecipeProperty;

/**
 * The machine picker's choices depend on the node's recipe, so its option list cannot be baked at
 * construction the way a tier list can. These pin the two shapes coexisting.
 */
class SettingOptionsTest {

    private static final RecipeProperty<String> MAP = RecipeProperty.<String>builder("test_map", "")
        .build();

    private static RecipeContext contextFor(final String recipeMap) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>();
        props.put(MAP, recipeMap);
        return new RecipeContext(props);
    }

    /**
     * The shared vocabulary must not carry another mod's tier names. Settings.VOLTAGE once held a
     * transcribed copy of GTValues.VN, which nothing rendered and which would have gone quietly wrong
     * the day GregTech added a tier; the coil, solenoid and casing rows are the same shape. Whether a
     * provider has attached a def is a different question - this only asserts that none is baked in.
     */
    @Test
    void theSharedVocabularyBakesInNoModsChoices() {
        final List<String> baked = new ArrayList<>();
        for (final Settings setting : Settings.values()) {
            if (setting == Settings.BURNABLE_OVERRIDE) continue; // PlanNH's own, not a mod's
            if (setting.def()
                .hasOptions()) baked.add(setting.key());
        }

        assertEquals(List.of(), baked, "these settings name another mod's choices in shared code");
    }

    @Test
    void staticEnumsKeepTheirBakedList() {
        final SettingDef<String> tier = SettingDef.enumDef("tier", "LV", List.of("LV", "MV", "HV"), (v, c) -> v);

        assertTrue(tier.hasOptions());
        assertEquals(List.of("LV", "MV", "HV"), tier.options(contextFor("anything")));
        assertEquals("MV", tier.display("MV"), "a static enum shows its stored value verbatim");
    }

    @Test
    void dynamicEnumsResolveAgainstTheRecipe() {
        final SettingDef<String> machine = SettingDef.dynamicEnumDef(
            "machine",
            "",
            ctx -> "gt.recipe.macerator".equals(ctx.getOrDefault(MAP, null)) ? List.of("id.stack", "id.basic")
                : List.of(),
            id -> id.replace("id.", "") + " machine",
            null);

        assertTrue(machine.hasOptions());
        assertEquals(List.of("id.stack", "id.basic"), machine.options(contextFor("gt.recipe.macerator")));
        assertTrue(
            machine.options(contextFor("gt.recipe.blastfurnace"))
                .isEmpty());
    }

    /** The saved value stays a stable id; only the rendered row goes through the display mapping. */
    @Test
    void displayMapsIdsToNamesWithoutChangingTheStoredValue() {
        final SettingDef<String> machine = SettingDef.dynamicEnumDef(
            "machine",
            "",
            ctx -> List.of("basicmachine.macerator.tier.01"),
            id -> "Basic Macerator",
            null);

        assertEquals("Basic Macerator", machine.display("basicmachine.macerator.tier.01"));
        assertEquals(
            "basicmachine.macerator.tier.01",
            machine.options(contextFor("x"))
                .getFirst());
    }

    @Test
    void anEmptyStaticListMeansNoRow() {
        final SettingDef<String> none = SettingDef.enumDef("none", "", List.of(), (v, c) -> v);

        assertFalse(none.hasOptions());
        assertTrue(
            none.options(contextFor("x"))
                .isEmpty());
    }

    /** withVisibility rebuilds the def, so it has to carry the option source across. */
    @Test
    void gatingADynamicEnumKeepsItsOptions() {
        final SettingDef<String> machine = SettingDef
            .dynamicEnumDef("machine", "", ctx -> List.of("a", "b"), id -> id.toUpperCase(), null)
            .withVisibility((ctx, s) -> false);

        assertEquals(List.of("a", "b"), machine.options(contextFor("x")));
        assertEquals("A", machine.display("a"));
        assertFalse(machine.isVisible(contextFor("x"), Map.of()));
    }
}
