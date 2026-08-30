package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;
import com.sbancuz.plannh.data.provider.gregtech.GTStructureTiers;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

import gregtech.api.enums.HeatingCoilLevel;

/**
 * The coil row stores a HeatingCoilLevel name but the preset formulas take GT's coil <em>tier</em>,
 * which is {@code ordinal - 2}. Getting that offset wrong shifts every heat overclock by two coil
 * steps and still looks plausible, so it is pinned here.
 */
class GTStructureSettingsTest {

    private static final RecipeContext EMPTY = new RecipeContext(new HashMap<RecipeProperty<?>, Object>());

    @Test
    void coilNamesAreListedInTierOrderStartingAtCupronickel() {
        assertEquals(GTStructureTiers.MAX_COIL_TIER + 1, GTSettings.COIL_NAMES.size());
        assertEquals(HeatingCoilLevel.LV.name(), GTSettings.COIL_NAMES.getFirst(), "tier 0 is Cupronickel (LV)");
        assertEquals(HeatingCoilLevel.MAX.name(), GTSettings.COIL_NAMES.getLast(), "tier 13 is Eternal (MAX)");
    }

    @Test
    void everyCoilNameRoundTripsToItsOwnTier() {
        for (int tier = 0; tier <= GTStructureTiers.MAX_COIL_TIER; tier++) {
            final HeatingCoilLevel level = HeatingCoilLevel.getFromTier((byte) tier);
            assertEquals(tier, GTSettings.COIL_NAMES.indexOf(level.name()), level.name());
            assertEquals(tier, level.getTier());
        }
    }

    @Test
    void aStoredCoilNameResolvesToThatTier() {
        final Map<String, Object> settings = Map.of(GTSettings.COIL, HeatingCoilLevel.HV.name());

        // HV is Nichrome: ordinal 4, so tier 2 and 3601K.
        assertEquals(
            2,
            GTSettings.resolve(EMPTY, settings, 5)
                .coilTier());
        assertEquals(3601, HeatingCoilLevel.HV.getHeat());
    }

    /** Unset settings open on the best structure; a planner should show the endgame number. */
    @Test
    void unsetKnobsDefaultToTheBestStructure() {
        final StructureState state = GTSettings.resolve(EMPTY, Map.of(), 5);

        assertEquals(GTStructureTiers.MAX_COIL_TIER, state.coilTier());
        assertEquals(GTStructureTiers.MAX_SOLENOID_TIER, state.solenoidTier());
        assertEquals(GTStructureTiers.MAX_ITEM_PIPE_TIER, state.itemPipeTier());
        assertEquals(GTStructureTiers.MAX_PIPE_CASING_TIER, state.pipeCasingTier());
        assertEquals(GTStructureTiers.MAX_WIDTH, state.width());
        assertEquals(5, state.voltageTier());
    }

    /** The row shows the block a player places, not GregTech's tier name for it. */
    @Test
    void theCoilRowReadsAsItsMaterial() {
        final String stored = GTSettings.COIL_NAMES.getFirst();

        assertEquals("LV", stored, "the stored value stays the locale-independent tier name");
        assertNotEquals(stored, GTSettings.COIL_DEF.display(stored), "but the row must not show LV");
    }

    /**
     * Both machines that read the setting take tier 1 as the Bronze Pipe Casing and count up from there,
     * so the row's own numbers are the tiers and the display is a lookup beside them. A tier outside
     * the range is a stored value from a pack with more casings, and must clamp rather than throw.
     */
    @Test
    void thePipeCasingRowNamesTheCasingAtEachTier() {
        assertEquals(4, GTStructureTiers.MAX_PIPE_CASING_TIER, "Bronze, Steel, Titanium, Tungstensteel");

        for (int tier = 1; tier <= GTStructureTiers.MAX_PIPE_CASING_TIER; tier++) {
            assertEquals(
                GTStructureTiers.pipeCasingName(tier),
                GTSettings.PIPE_CASING_DEF.display(String.valueOf(tier)),
                "the row and the tier table must name the same casing");
        }
        assertEquals(GTStructureTiers.pipeCasingName(1), GTStructureTiers.pipeCasingName(0));
        assertEquals(
            GTStructureTiers.pipeCasingName(GTStructureTiers.MAX_PIPE_CASING_TIER),
            GTStructureTiers.pipeCasingName(99));
    }

    /** A junk coil name must not silently read as Cupronickel; indexOf returning -1 is visible. */
    @Test
    void anUnknownCoilNameDoesNotMasqueradeAsTierZero() {
        final StructureState state = GTSettings.resolve(EMPTY, Map.of(GTSettings.COIL, "NOT_A_COIL"), 5);

        assertNotEquals(0, state.coilTier());
        assertEquals(-1, state.coilTier());
    }
}
