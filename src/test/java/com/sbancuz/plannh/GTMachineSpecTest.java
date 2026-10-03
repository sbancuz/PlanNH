package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.provider.gregtech.GTMachineSpec;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

import gregtech.api.logic.MachineMode;
import gregtech.api.logic.ModifierKind;
import gregtech.api.logic.ProcessingSpec;
import gregtech.api.recipe.RecipeMap;

/**
 * Spec math is GregTech's, so these test only PlanNH's additions: the inputs a node's state maps to, and when a machine
 * gets rows.
 */
class GTMachineSpecTest {

    private static final ModifierKind.IntKind MOMENTUM = ModifierKind.ofInt("plannh_test:momentum")
        .source(ModifierKind.Source.RUNTIME)
        .ordered()
        .range(0, 100)
        .register();

    private static final List<MachineMode> TWO_MODES = List
        .of(MachineMode.of(mock(RecipeMap.class)), MachineMode.of(mock(RecipeMap.class)));

    @Test
    void anUnsetValueIsTheSpecsBest() {
        final GTMachineSpec machine = GTMachineSpec.of(
            ProcessingSpec.builder()
                .parallel(in -> 2 * in.value(ModifierKind.COIL), ModifierKind.COIL)
                .parallelPerTier(3, ModifierKind.VOLTAGE)
                .build());
        final int bestCoil = (int) ModifierKind.COIL.getRange()
            .max();

        assertEquals(
            2 * bestCoil + 3 * 5,
            machine.spec()
                .getMaxParallel(machine.inputs(StructureState.of(5, 0))));
        assertEquals(
            2 * 4 + 3 * 5,
            machine.spec()
                .getMaxParallel(machine.inputs(new StructureState(5, 1, 0, Map.of(ModifierKind.COIL, 4L)))));
    }

    /**
     * An untouched node is planned at the chart's floor unless the recipe requires more: a coil too cold for the recipe
     * is raised to the coolest that passes GregTech's check, voltage bonus included.
     */
    @Test
    void anUnsetValueIsRaisedToTheLowestThatRunsTheRecipe() {
        final GTMachineSpec ebf = GTMachineSpec.of(
            ProcessingSpec.builder()
                .coilHeatPerVoltageTier(100, 2, ProcessingSpec.HeatRule.REQUIRED)
                .build());
        final StructureState atCupronickel = new StructureState(5, 1, 0, Map.of(), Map.of(ModifierKind.COIL, 0L));
        final StructureState chosen = new StructureState(5, 1, 0, Map.of(ModifierKind.COIL, 0L));

        // Nichrome (coil tier 2) is 3601K, plus 300K at IV
        assertEquals(
            2,
            ebf.inputs(atCupronickel, GTSpecs.recipe(120, 100, 3901))
                .value(ModifierKind.COIL));
        assertEquals(
            3,
            ebf.inputs(atCupronickel, GTSpecs.recipe(120, 100, 3902))
                .value(ModifierKind.COIL));
        assertEquals(
            0,
            ebf.inputs(chosen, GTSpecs.recipe(120, 100, 3902))
                .value(ModifierKind.COIL),
            "what the player chose stays, and the node shows why it cannot run");
    }

    @Test
    void aSpecThatReadsAnUndeclaredKindFails() {
        assertThrows(
            IllegalArgumentException.class,
            () -> GTMachineSpec.of(
                ProcessingSpec.builder()
                    .parallel(in -> in.value(ModifierKind.SOLENOID))
                    .build()));
    }

    /** A value built up while running is planned at its best, so it has no row. */
    @Test
    void onlyBuiltValuesAreRows() {
        final GTMachineSpec machine = GTMachineSpec.of(
            ProcessingSpec.builder()
                .parallelPerVoltageTierRising(4, 8, MOMENTUM, 100)
                .speedRising(2, 4, MOMENTUM, 100)
                .speedPerTier(1, 1, ModifierKind.ITEM_PIPE_CASING)
                .build());

        assertEquals(
            List.of(ModifierKind.ITEM_PIPE_CASING),
            List.copyOf(
                machine.structure()
                    .keySet()));
        assertEquals(
            8 * 5,
            machine.spec()
                .getMaxParallel(machine.inputs(StructureState.of(5, 0))),
            "full momentum");
    }

    @Test
    void aModeRowOnlyWhenTheModeMatters() {
        final ProcessingSpec byMode = ProcessingSpec.builder()
            .modes(TWO_MODES)
            .inMode(1, mode -> mode.parallel(32))
            .build();

        assertTrue(
            GTMachineSpec.of(byMode)
                .settings()
                .contains(Settings.GT_MODE));
        assertFalse(
            GTMachineSpec.of(ProcessingSpec.STANDARD)
                .settings()
                .contains(Settings.GT_MODE));
    }

    /** The node's amps reach the spec as one hatch with all of them, so a spec using every amp reads them all. */
    @Test
    void theNodesAmpsReachTheSpec() {
        final GTMachineSpec machine = GTMachineSpec.of(
            ProcessingSpec.builder()
                .allAmps()
                .build());

        assertEquals(
            4,
            machine.spec()
                .getPower(machine.inputs(new StructureState(5, 4, 0, Map.of())))
                .amperage());
    }
}
