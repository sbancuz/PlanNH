package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset;
import com.sbancuz.plannh.data.provider.gregtech.GTMachinePresets;
import com.sbancuz.plannh.data.provider.gregtech.GTStructureTiers;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

import gregtech.api.enums.HeatingCoilLevel;

/**
 * The preset table is a hand-copied mirror of ~30 MetaTileEntities, pinned to one GT version. It
 * cannot be checked by re-deriving the same arithmetic, so these assert the things that actually go
 * wrong: keys that stop resolving, and formulas whose direction is inverted.
 */
class GTMachinePresetTest {

    private static StructureState state(final int voltageTier, final int coilTier) {
        return new StructureState(voltageTier, coilTier, 4, 4, 2, 0, 0, 1, 0, 0);
    }

    static List<String> presetKeys() {
        final List<String> keys = new ArrayList<>();
        GTMachinePresets.keys()
            .forEach(keys::add);
        return keys;
    }

    /**
     * A renamed or moved MetaTileEntity must fail here rather than silently falling through to "no
     * preset" at runtime, which would look like a machine GT simply does not have.
     */
    @ParameterizedTest
    @MethodSource("presetKeys")
    void everyKeyedClassStillExists(final String className) {
        try {
            Class.forName(className, false, GTMachinePresetTest.class.getClassLoader());
        } catch (final ClassNotFoundException e) {
            fail("preset keyed on a class that no longer exists at the pinned GT version: " + className);
        }
    }

    @Test
    void lookupWalksSuperclasses() throws ClassNotFoundException {
        final Class<?> ebf = Class.forName(
            "gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace",
            false,
            getClass().getClassLoader());

        assertNotNull(GTMachinePresets.lookup(ebf));
        assertTrue(
            GTMachinePresets.lookup(ebf)
                .usesHeat());
    }

    @Test
    void anUnknownMachineHasNoPreset() {
        assertEquals(null, GTMachinePresets.lookup(String.class));
    }

    /**
     * The EBF's machine heat is the coil plus 100K per voltage tier above MV. Getting the
     * HeatingCoilLevel tier offset wrong (getTier() is ordinal - 2) silently shifts every heat
     * overclock by two coil steps.
     */
    @Test
    void blastFurnaceHeatMatchesCoilPlusVoltageBonus() throws ClassNotFoundException {
        final GTMachinePreset ebf = GTMachinePresets.lookup(
            Class.forName(
                "gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace",
                false,
                getClass().getClassLoader()));

        // Cupronickel is tier 0 and 1801K; at MV (tier 2) the voltage bonus is exactly zero.
        assertEquals(
            1801,
            HeatingCoilLevel.getFromTier((byte) 0)
                .getHeat());
        assertEquals(
            1801,
            ebf.machineHeat()
                .applyAsInt(state(2, 0)));
        assertEquals(
            1801 + 300,
            ebf.machineHeat()
                .applyAsInt(state(5, 0)));
    }

    /**
     * The Plasma Forge sets a heat from its coil, but spends it on deciding which recipes will run
     * rather than on overclocking - GregTech never calls {@code setHeatOC} for it. Conflating it with
     * the EBF gave it overclocks it does not have, so both halves are pinned here.
     */
    @Test
    void plasmaForgeHeatGatesRecipesAndDoesNotOverclock() throws ClassNotFoundException {
        final GTMachinePreset dtpf = GTMachinePresets.lookup(
            Class.forName(
                "gregtech.common.tileentities.machines.multi.MTEPlasmaForge",
                false,
                getClass().getClassLoader()));

        assertFalse(dtpf.heatOC(), "GregTech does not overclock the Plasma Forge on heat");
        assertFalse(dtpf.usesHeat(), "so the calculator must not be given a heat at all");
        assertEquals(
            dtpf.machineHeat()
                .applyAsInt(state(2, 5)),
            dtpf.machineHeat()
                .applyAsInt(state(9, 5)),
            "voltage must not change Plasma Forge heat");
    }

    /** More coil is never worse: faster or equal, and never more EU per tick. */
    @ParameterizedTest
    @MethodSource("presetKeys")
    void coilDrivenFormulasImproveMonotonically(final String className) throws ClassNotFoundException {
        final GTMachinePreset preset = GTMachinePresets
            .lookup(Class.forName(className, false, getClass().getClassLoader()));
        assertNotNull(preset);
        if (!preset.knobs()
            .contains(GTMachinePreset.Knob.COIL)) return;

        for (int coil = 0; coil < GTStructureTiers.MAX_COIL_TIER; coil++) {
            final StructureState low = state(5, coil);
            final StructureState high = state(5, coil + 1);

            assertTrue(
                preset.durationModifier()
                    .applyAsDouble(high)
                    <= preset.durationModifier()
                        .applyAsDouble(low) + 1e-9,
                className + ": coil " + (coil + 1) + " is slower than coil " + coil);
            assertTrue(
                preset.euModifier()
                    .applyAsDouble(high)
                    <= preset.euModifier()
                        .applyAsDouble(low) + 1e-9,
                className + ": coil " + (coil + 1) + " costs more EU than coil " + coil);
            assertTrue(
                preset.machineHeat()
                    .applyAsInt(high)
                    >= preset.machineHeat()
                        .applyAsInt(low),
                className + ": coil " + (coil + 1) + " is colder than coil " + coil);
        }
    }

    /** A bigger machine never runs fewer recipes at once. */
    @ParameterizedTest
    @MethodSource("presetKeys")
    void parallelNeverShrinksWithVoltage(final String className) throws ClassNotFoundException {
        final GTMachinePreset preset = GTMachinePresets
            .lookup(Class.forName(className, false, getClass().getClassLoader()));
        assertNotNull(preset);

        for (int tier = 1; tier < 14; tier++) {
            assertTrue(
                preset.maxParallel()
                    .applyAsInt(state(tier + 1, 5))
                    >= preset.maxParallel()
                        .applyAsInt(state(tier, 5)),
                className + ": parallel shrinks going from tier " + tier + " to " + (tier + 1));
        }
    }

    /** Nothing may return a zero or negative parallel; that would zero out a node's throughput. */
    @ParameterizedTest
    @MethodSource("presetKeys")
    void parallelIsAlwaysPositive(final String className) throws ClassNotFoundException {
        final GTMachinePreset preset = GTMachinePresets
            .lookup(Class.forName(className, false, getClass().getClassLoader()));
        assertNotNull(preset);

        for (int tier = 1; tier <= 14; tier++) {
            assertTrue(
                preset.maxParallel()
                    .applyAsInt(state(tier, 0)) >= 1,
                className + ": non-positive parallel at tier " + tier);
        }
    }

    /** Duration and EU multipliers are ratios; a non-positive one would invert or zero the recipe. */
    @ParameterizedTest
    @MethodSource("presetKeys")
    void modifiersStayPositive(final String className) throws ClassNotFoundException {
        final GTMachinePreset preset = GTMachinePresets
            .lookup(Class.forName(className, false, getClass().getClassLoader()));
        assertNotNull(preset);

        for (int coil = 0; coil <= GTStructureTiers.MAX_COIL_TIER; coil++) {
            final StructureState s = state(5, coil);
            assertTrue(
                preset.durationModifier()
                    .applyAsDouble(s) > 0,
                className + ": duration modifier <= 0");
            assertTrue(
                preset.euModifier()
                    .applyAsDouble(s) > 0,
                className + ": eu modifier <= 0");
            assertTrue(
                preset.eutIncreasePerOC()
                    .applyAsDouble(s) > 0,
                className + ": eutIncreasePerOC <= 0");
            assertTrue(
                preset.durationDecreasePerOC()
                    .applyAsDouble(s) > 0,
                className + ": durationDecreasePerOC <= 0");
        }
    }
}
