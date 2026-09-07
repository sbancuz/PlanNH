package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.provider.gregtech.GTMachineOverrides;
import com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset;
import com.sbancuz.plannh.data.provider.gregtech.GTPresetApplier;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.util.GTUtility;
import gregtech.api.util.OverclockCalculator;

/**
 * Checks that a preset configures {@link OverclockCalculator} the way a hand-written GregTech call
 * would. The oracle is GT's calculator itself, never a reimplementation of its arithmetic - these
 * would still pass if GT changed how overclocking works, and fail if PlanNH wired a setting to the
 * wrong setter.
 */
class GTPresetApplierTest {

    private static final String EBF = "gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace";
    private static final String MULTI_SMELTER = "gregtech.common.tileentities.machines.multi.MTEMultiFurnace";

    private static GTMachinePreset preset(final String className) throws ClassNotFoundException {
        final GTMachinePreset found = GTMachineOverrides
            .preset(Class.forName(className, false, GTPresetApplierTest.class.getClassLoader()));
        assertNotNull(found, className);
        return found;
    }

    private static StructureState state(final int voltageTier, final int coilTier) {
        return new StructureState(voltageTier, coilTier, 4, 4, 2, 0, 0, 1, 0, 0);
    }

    /**
     * The EBF is the machine where the most can go wrong: coil heat, the voltage bonus, heat
     * overclocks and the heat discount all at once. GT's own unit tests use exactly this shape.
     */
    @Test
    void blastFurnacePresetMatchesAHandWrittenGregTechCall() throws ClassNotFoundException {
        final int coilTier = 8;
        final int voltageTier = 5;
        final int recipeHeat = 1800;
        final int machineHeat = (int) HeatingCoilLevel.getFromTier((byte) coilTier)
            .getHeat() + 100 * (voltageTier - 2);

        final OverclockCalculator expected = new OverclockCalculator().setRecipeEUt(GTValues.VP[1])
            .setEUt(GTValues.V[voltageTier])
            .setDuration(1024)
            .setAmperage(1)
            .setHeatOC(true)
            .setHeatDiscount(true)
            .setRecipeHeat(recipeHeat)
            .setMachineHeat(machineHeat)
            .setParallel(1)
            .setAmperageOC(true)
            .calculate();

        final OverclockCalculator actual = GTPresetApplier
            .buildFromPreset(
                preset(EBF),
                state(voltageTier, coilTier),
                GTValues.VP[1],
                1024,
                GTValues.V[voltageTier],
                1,
                recipeHeat)
            .setParallel(1)
            .setAmperageOC(true)
            .calculate();

        assertEquals(expected.getDuration(), actual.getDuration());
        assertEquals(expected.getConsumption(), actual.getConsumption());
        assertTrue(actual.getDuration() < 1024, "heat overclocks should have applied at all");
    }

    /** The heat discount is 0.95 per 900K of headroom; wiring euModifier to it instead would compound. */
    @Test
    void blastFurnaceHeatDiscountIsGregTechs() throws ClassNotFoundException {
        final int coilTier = 8;
        final int machineHeat = (int) HeatingCoilLevel.getFromTier((byte) coilTier)
            .getHeat() + 100 * (5 - 2);
        final int discounts = (machineHeat - 1800) / 900;

        final OverclockCalculator calc = GTPresetApplier
            .buildFromPreset(preset(EBF), state(5, coilTier), GTValues.VP[1], 1024, GTValues.V[5], 1, 1800);

        assertEquals(GTUtility.powInt(0.95, discounts), calc.calculateHeatDiscountMultiplier(), 1e-9);
    }

    /** Perfect overclock is 4x duration per 4x EU, not GT's default 2x per 4x. */
    @Test
    void perfectOverclockHalvesDurationTwiceAsFast() {
        final GTMachinePreset perfectOC = GTMachinePreset.builder()
            .perfectOC()
            .build();

        final OverclockCalculator perfect = GTPresetApplier
            .buildFromPreset(perfectOC, state(5, 0), GTValues.VP[1], 1024, GTValues.V[5], 1, 0)
            .setParallel(1)
            .setAmperageOC(true)
            .calculate();

        final OverclockCalculator plain = new OverclockCalculator().setRecipeEUt(GTValues.VP[1])
            .setEUt(GTValues.V[5])
            .setDuration(1024)
            .setParallel(1)
            .setAmperageOC(true)
            .calculate();

        assertTrue(
            perfect.getDuration() < plain.getDuration(),
            "perfect OC must be faster than the default for the same overclock count");
        assertEquals(plain.getConsumption(), perfect.getConsumption(), "perfect OC changes speed, not power");
    }

    /**
     * Tier skipping is how far <em>above</em> the machine's own voltage a recipe may sit, so it only
     * shows up on a recipe the machine could not otherwise run. A ZPM recipe is four tiers over an
     * IV machine: out of reach at GT's default of one skip, fine for a preset that lifts the limit.
     */
    @Test
    void unlimitedTierSkipsReachAFourTierGap() {
        final GTMachinePreset unlimited = GTMachinePreset.builder()
            .unlimitedTierSkips()
            .build();

        final OverclockCalculator forge = GTPresetApplier
            .buildFromPreset(unlimited, state(5, 8), GTValues.V[7], 1024, GTValues.V[5], 1, 1800);
        final OverclockCalculator defaultLimit = new OverclockCalculator().setRecipeEUt(GTValues.V[7])
            .setEUt(GTValues.V[5])
            .setDuration(1024);

        assertTrue(forge.getAllowedTierSkip(), "an unlimited-skip preset lifts the limit");
        assertTrue(!defaultLimit.getAllowedTierSkip(), "GT's default of one skip does not reach four tiers");
    }

    /**
     * A preset may forbid skipping outright, so it cannot even reach one tier up - which is exactly
     * what the settings-map path cannot express, since 0 there means "unset". Hence the preset's own
     * sentinel.
     */
    @Test
    void zeroTierSkipsRefusesEvenOneTier() {
        final GTMachinePreset arc = GTMachinePreset.builder()
            .maxTierSkips(0)
            .build();
        assertEquals(0, arc.maxTierSkips());

        // One tier up: allowed by GT's default of a single skip, refused with skipping disabled.
        final OverclockCalculator noSkips = GTPresetApplier
            .buildFromPreset(arc, state(5, 0), GTValues.V[6], 1024, GTValues.V[5], 1, 0);
        final OverclockCalculator defaultLimit = new OverclockCalculator().setRecipeEUt(GTValues.V[6])
            .setEUt(GTValues.V[5])
            .setDuration(1024);

        assertTrue(!noSkips.getAllowedTierSkip(), "an EV recipe must not run in an IV machine");
        assertTrue(defaultLimit.getAllowedTierSkip(), "GT's default would have allowed it");
    }

    /** The Multi Smelter ignores the recipe's own cost entirely: always 4 EU/t over 128 ticks. */
    @Test
    void multiSmelterOverridesTheRecipeCost() throws ClassNotFoundException {
        final GTMachinePreset smelter = preset(MULTI_SMELTER);
        assertNotNull(smelter.recipeOverride());
        assertEquals(
            4,
            smelter.recipeOverride()
                .eut());
        assertEquals(
            128,
            smelter.recipeOverride()
                .duration());
    }

    /** A machine with no preset must still yield a usable calculator rather than throwing. */
    @Test
    void anAbsentPresetFallsBackToAPlainCalculator() {
        final OverclockCalculator calc = GTPresetApplier
            .buildFromPreset(null, state(5, 0), GTValues.VP[1], 1024, GTValues.V[5], 1, 0)
            .setParallel(1)
            .setAmperageOC(true)
            .calculate();

        assertTrue(calc.getDuration() > 0);
        assertTrue(calc.getConsumption() > 0);
    }
}
