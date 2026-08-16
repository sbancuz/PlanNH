package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob;
import com.sbancuz.plannh.data.provider.gregtech.probe.FieldInjector;

/**
 * The probe reaches a machine's structure through the instance fields its {@code checkMachine} would
 * have written, recognised by type or by name. A GregTech rename would leave the field unwritten, the
 * machine would answer for a structure nobody built, and nothing would say so - so the names are
 * asserted against real machines here.
 *
 * <p>
 * Classes are loaded without initializing them, which is what lets a MetaTileEntity be reflected over
 * outside a client.
 */
class GTFieldInjectorTest {

    private static final String GT_MULTI = "gregtech.common.tileentities.machines.multi.";
    private static final String GTPP = "gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.";

    /**
     * Each machine must still expose at least the knobs its hand-written preset row declares. More is
     * allowed and expected: a field being present only means the machine stores it, not that any
     * number moves with it, which is what the sensitivity scan settles.
     */
    @ParameterizedTest
    @CsvSource({ GT_MULTI + "MTEIndustrialThermalCentrifuge,COIL|SOLENOID",
        GT_MULTI + "MTEIndustrialCokeOven,COIL|WIDTH|STRUCTURE_TIER", GT_MULTI + "MTEIndustrialMixer,ITEM_PIPE",
        GT_MULTI + "MTEIndustrialWireMill,ITEM_PIPE", GT_MULTI + "MTEIndustrialMacerator,STRUCTURE_TIER",
        GT_MULTI + "MTEMegaOilCracker,COIL", GT_MULTI + "MTEPyrolyseOven,COIL",
        GT_MULTI + "MTEElectricBlastFurnace,COIL", GTPP + "production.chemplant.MTEChemicalPlant,COIL|PIPE_CASING",
        GTPP + "processing.MTEIndustrialAlloySmelter,COIL", GTPP + "processing.advanced.MTEAdvEBF,COIL" })
    void theStructureFieldsAreStillReachable(final String className, final String expected) {
        final EnumSet<Knob> reachable = FieldInjector.forClass(uninitialised(className))
            .reachableKnobs();
        for (final String name : expected.split("\\|")) {
            final Knob knob = Knob.valueOf(name);
            assertTrue(reachable.contains(knob), className + " no longer exposes " + knob + ", found " + reachable);
        }
    }

    /**
     * Every multiblock inherits {@code machineMode}, so the field alone would put a mode row on all of
     * them. A machine that really has modes answers {@code supportsMachineModeSwitch} for itself, and
     * that is what the probe goes on.
     */
    @Test
    void onlyMachinesThatDeclareModesExposeTheModeKnob() {
        assertTrue(
            FieldInjector.forClass(uninitialised(GT_MULTI + "MTEOreWashingPlant"))
                .reachableKnobs()
                .contains(Knob.MODE),
            "MTEOreWashingPlant no longer declares supportsMachineModeSwitch");
        assertFalse(
            FieldInjector.forClass(uninitialised(GT_MULTI + "MTEIndustrialSifter"))
                .reachableKnobs()
                .contains(Knob.MODE),
            "the Industrial Sifter has no modes, so it must not offer the row");
    }

    private static Class<?> uninitialised(final String className) {
        try {
            return Class.forName(className, false, GTFieldInjectorTest.class.getClassLoader());
        } catch (final ClassNotFoundException e) {
            return fail("GregTech no longer ships " + className, e);
        }
    }
}
