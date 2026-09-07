package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sbancuz.plannh.data.provider.gregtech.GTMachineOverrides;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

/**
 * The override file is the only place PlanNH still asserts a number against the machine that owns it,
 * so its contract is worth pinning: every row names a machine that exists, and every row says why it
 * is there - the reason is what takes the machine off the probe.
 */
class GTMachineOverridesTest {

    private static final String EBF = "gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace";
    private static final String MULTI_FURNACE = "gregtech.common.tileentities.machines.multi.MTEMultiFurnace";

    private static Class<?> uninitialised(final String className) {
        try {
            return Class.forName(className, false, GTMachineOverridesTest.class.getClassLoader());
        } catch (final ClassNotFoundException e) {
            return fail("override keyed on a class that no longer exists: " + className, e);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { EBF, MULTI_FURNACE, "bartworks.common.tileentities.multis.MTECircuitAssemblyLine" })
    void everyOverrideNamesAMachineAndSaysWhy(final String className) {

        final String reason = GTMachineOverrides.reason(uninitialised(className));
        assertNotNull(reason, className + " must state why it is not read from GregTech");
        assertFalse(reason.isBlank(), className + " must state why it is not read from GregTech");
    }

    @Test
    void aMachineWithNoOverrideHasNoReason() {
        assertNull(
            GTMachineOverrides.reason(uninitialised("gregtech.common.tileentities.machines.multi.MTEIndustrialSifter")),
            "only overridden machines may carry a reason: it is what takes them off the probe");
    }

    /** An overridden machine resolves to its own row, including through a superclass walk. */
    @Test
    void anOverrideWinsTheLookup() {
        final var ebf = GTMachineOverrides.preset(uninitialised(EBF));
        assertNotNull(ebf);

        // The voltage term is the reason the EBF is overridden at all, so it is what proves the win.
        final StructureState mv = new StructureState(2, 0, 4, 4, 2, 0, 0, 1, 0, 0);
        final StructureState hv = new StructureState(3, 0, 4, 4, 2, 0, 0, 1, 0, 0);
        assertEquals(
            100,
            ebf.machineHeat()
                .applyAsInt(hv)
                - ebf.machineHeat()
                    .applyAsInt(mv),
            "the EBF override exists to add 100K per voltage tier");
    }
}
