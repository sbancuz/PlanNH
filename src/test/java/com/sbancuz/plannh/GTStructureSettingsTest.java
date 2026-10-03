package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.logic.ModifierKind;

/**
 * Every structure row stores its kind's number in GregTech's numbering, so a coil row and a solenoid row work the same.
 * Charts that stored the coil row as GregTech's coil level name are migrated on load.
 */
class GTStructureSettingsTest {

    private static final RecipeContext EMPTY = new RecipeContext(new HashMap<RecipeProperty<?>, Object>());

    @Test
    void theCoilRowIsTheCoilKindsRow() {
        assertEquals("gt_coil", GTSettings.COIL, "charts saved before kinds were namespaced use this key");
        assertEquals(GTSettings.COIL, GTSettings.structureKey(ModifierKind.COIL));
        assertEquals("gt_pipe_casing", GTSettings.structureKey(ModifierKind.PIPE_CASING));
    }

    @Test
    void aStoredValueIsWhatThePlayerChose() {
        final StructureState state = GTSettings.resolve(EMPTY, Map.of(GTSettings.COIL, 2), 5);

        assertEquals(
            2L,
            state.structure()
                .get(ModifierKind.COIL));
    }

    /** Unset values open on the chart's floor, or the best value on a chart with no floor. */
    @Test
    void unsetValuesOpenOnTheChartsFloor() {
        final StructureState state = GTSettings.resolve(EMPTY, Map.of(), 5);

        assertTrue(
            state.structure()
                .isEmpty());
        assertEquals(
            ModifierKind.COIL.getRange()
                .max(),
            state.floors()
                .get(ModifierKind.COIL));
        assertEquals(
            ModifierKind.PIPE_CASING.getRange()
                .max(),
            state.floors()
                .get(ModifierKind.PIPE_CASING));
        assertFalse(
            state.floors()
                .containsKey(ModifierKind.SOLENOID),
            "anything else reads as the spec's best");
        assertEquals(5, state.voltageTier());
    }

    /** HV is Nichrome, GregTech's coil tier 2. */
    @Test
    void aSavedCoilNameBecomesItsTier() {
        final Map<String, Object> settings = new HashMap<>(Map.of(GTSettings.COIL, HeatingCoilLevel.HV.name()));

        GTSettings.migrateLegacyNode(settings);

        assertEquals(2, settings.get(GTSettings.COIL));
    }

    /** A junk name must not read as Cupronickel. */
    @Test
    void aSavedNameNoCoilHasIsDropped() {
        final Map<String, Object> settings = new HashMap<>(Map.of(GTSettings.COIL, "NOT_A_COIL"));

        GTSettings.migrateLegacyNode(settings);

        assertFalse(settings.containsKey(GTSettings.COIL));
    }
}
