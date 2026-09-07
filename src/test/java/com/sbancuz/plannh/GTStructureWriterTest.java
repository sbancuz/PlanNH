package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.provider.gregtech.probe.StructureWriter;

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
class GTStructureWriterTest {

    private static final String GT_MULTI = "gregtech.common.tileentities.machines.multi.";
    private static final String GTPP = "gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.";

    /**
     * Each machine must still expose at least the settings its hand-written preset row declares. More is
     * allowed and expected: a field being present only means the machine stores it, not that any
     * number moves with it, which is what the sensitivity scan settles.
     */
    @ParameterizedTest
    @CsvSource({ GT_MULTI + "MTEIndustrialThermalCentrifuge,GT_COIL|GT_SOLENOID",
        GT_MULTI + "MTEIndustrialCokeOven,GT_COIL|GT_WIDTH|GT_STRUCTURE_TIER",
        GT_MULTI + "MTEIndustrialMixer,GT_ITEM_PIPE", GT_MULTI + "MTEIndustrialWireMill,GT_ITEM_PIPE",
        GT_MULTI + "MTEIndustrialMacerator,GT_STRUCTURE_TIER", GT_MULTI + "MTEMegaOilCracker,GT_COIL",
        GT_MULTI + "MTEPyrolyseOven,GT_COIL", GT_MULTI + "MTEElectricBlastFurnace,GT_COIL",
        GTPP + "production.chemplant.MTEChemicalPlant,GT_COIL|GT_PIPE_CASING",
        GTPP + "processing.MTEIndustrialAlloySmelter,GT_COIL", GTPP + "processing.advanced.MTEAdvEBF,GT_COIL" })
    void theStructureFieldsAreStillReachable(final String className, final String expected) {
        final EnumSet<Settings> reachable = StructureWriter.forClass(uninitialised(className))
            .reachableSettings();
        for (final String name : expected.split("\\|")) {
            final Settings setting = Settings.valueOf(name);
            assertTrue(
                reachable.contains(setting),
                className + " no longer exposes " + setting + ", found " + reachable);
        }
    }

    /**
     * Every multiblock inherits {@code machineMode}, so the field alone would put a mode row on all of
     * them. A machine that really has modes answers {@code supportsMachineModeSwitch} for itself, and
     * that is what the probe goes on.
     */
    @Test
    void onlyMachinesThatDeclareModesExposeTheModeSetting() {
        assertTrue(
            StructureWriter.forClass(uninitialised(GT_MULTI + "MTEOreWashingPlant"))
                .reachableSettings()
                .contains(Settings.GT_MODE),
            "MTEOreWashingPlant no longer declares supportsMachineModeSwitch");
        assertFalse(
            StructureWriter.forClass(uninitialised(GT_MULTI + "MTEIndustrialSifter"))
                .reachableSettings()
                .contains(Settings.GT_MODE),
            "the Industrial Sifter has no modes, so it must not offer the row");
    }

    private static Class<?> uninitialised(final String className) {
        try {
            return Class.forName(className, false, GTStructureWriterTest.class.getClassLoader());
        } catch (final ClassNotFoundException e) {
            return fail("GregTech no longer ships " + className, e);
        }
    }
}
