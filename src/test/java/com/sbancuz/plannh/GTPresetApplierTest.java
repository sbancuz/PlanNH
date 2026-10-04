package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.provider.gregtech.StructureState;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.VoltageIndex;
import gregtech.api.logic.ModifierKind;
import gregtech.api.logic.ProcessingSpec;
import gregtech.api.util.OverclockCalculator;

/**
 * Checks that a machine's spec configures {@link OverclockCalculator} like a hand-written GregTech call.
 * The oracle is GT's calculator, not a copy of its arithmetic. These pass if GT changes how
 * overclocking works, and fail if PlanNH wires a setting to the wrong setter.
 */
class GTPresetApplierTest {

    /** EBF heat as GregTech defines it: coil heat plus 100K per tier over MV. */
    private static ProcessingSpec ebfShaped() {
        return ProcessingSpec.builder()
            .coilHeatPerVoltageTier(
                100,
                VoltageIndex.MV,
                ProcessingSpec.HeatRule.OVERCLOCK,
                ProcessingSpec.HeatRule.DISCOUNT)
            .build();
    }

    /** Read off the spec, not recomputed here. The tests cover the wiring, not the heat formula. */
    private static int machineHeat(final StructureState state) {
        return ebfShaped().getHeat()
            .orElseThrow()
            .getMachineHeat(
                GTSpecs.machine(ebfShaped())
                    .inputs(state));
    }

    private static StructureState state(final int voltageTier, final int coilTier) {
        return new StructureState(voltageTier, 1, 0, Map.of(ModifierKind.COIL, (long) coilTier));
    }

    /**
     * The EBF combines the most moving parts: coil heat, the voltage bonus, heat overclocks and the
     * heat discount. GT's unit tests use this shape too.
     */
    @Test
    void blastFurnaceSpecMatchesAHandWrittenGregTechCall() {
        final int coilTier = 8;
        final int voltageTier = 5;
        final int recipeHeat = 1800;
        // read off the spec, not recomputed: this test covers the wiring, not the heat formula
        final int machineHeat = machineHeat(state(voltageTier, coilTier));

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

        final OverclockCalculator actual = GTSpecs
            .calculator(ebfShaped(), state(voltageTier, coilTier), GTValues.VP[1], 1024, recipeHeat)
            .setParallel(1)
            .setAmperageOC(true)
            .calculate();

        assertEquals(expected.getDuration(), actual.getDuration());
        assertEquals(expected.getConsumption(), actual.getConsumption());
        assertTrue(actual.getDuration() < 1024, "heat overclocks should have applied at all");
    }

    /** The discount must come from GregTech's calculator, not a second implementation wired into euModifier. */
    @Test
    void blastFurnaceHeatDiscountIsGregTechs() {
        final int coilTier = 8;
        final int machineHeat = machineHeat(state(5, coilTier));

        final OverclockCalculator expected = new OverclockCalculator().setRecipeEUt(GTValues.VP[1])
            .setEUt(GTValues.V[5])
            .setDuration(1024)
            .setHeatDiscount(true)
            .setRecipeHeat(1800)
            .setMachineHeat(machineHeat);

        final OverclockCalculator calc = GTSpecs
            .calculator(ebfShaped(), state(5, coilTier), GTValues.VP[1], 1024, 1800);

        assertEquals(expected.calculateHeatDiscountMultiplier(), calc.calculateHeatDiscountMultiplier(), 1e-9);
    }

    /** Perfect overclock is 4x duration per 4x EU, not GT's default 2x per 4x. */
    @Test
    void perfectOverclockHalvesDurationTwiceAsFast() {
        final ProcessingSpec perfectOC = ProcessingSpec.builder()
            .overclock(4, 4)
            .build();

        final OverclockCalculator perfect = GTSpecs.calculator(perfectOC, state(5, 0), GTValues.VP[1], 1024, 0)
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
     * Tier skipping is how far <em>above</em> the machine's voltage a recipe may be, so it only matters
     * for a recipe the machine could not otherwise run. A ZPM recipe is four tiers over an IV machine:
     * out of reach at GT's default of one skip, runnable for a spec that lifts the limit.
     */
    @Test
    void unlimitedTierSkipsReachAFourTierGap() {
        final ProcessingSpec unlimited = ProcessingSpec.builder()
            .unlimitedTierSkips()
            .build();

        final OverclockCalculator forge = GTSpecs.calculator(unlimited, state(5, 8), GTValues.V[7], 1024, 1800);
        final OverclockCalculator defaultLimit = new OverclockCalculator().setRecipeEUt(GTValues.V[7])
            .setEUt(GTValues.V[5])
            .setDuration(1024);

        assertTrue(forge.getRecipeEUt() <= forge.getMaxAllowedRecipeEUt(), "an unlimited-skip spec lifts the limit");
        assertTrue(
            defaultLimit.getRecipeEUt() > defaultLimit.getMaxAllowedRecipeEUt(),
            "GT's default of one skip does not reach four tiers");
    }

    /** A spec that disables skipping cannot run a recipe even one tier up. */
    @Test
    void zeroTierSkipsRefusesEvenOneTier() {
        final ProcessingSpec arc = ProcessingSpec.builder()
            .maxTierSkips(0)
            .build();

        // one tier up: allowed by GT's default of a single skip, refused with skipping disabled
        final OverclockCalculator noSkips = GTSpecs.calculator(arc, state(5, 0), GTValues.V[6], 1024, 0);
        final OverclockCalculator defaultLimit = new OverclockCalculator().setRecipeEUt(GTValues.V[6])
            .setEUt(GTValues.V[5])
            .setDuration(1024);

        assertTrue(
            noSkips.getRecipeEUt() > noSkips.getMaxAllowedRecipeEUt(),
            "an EV recipe must not run in an IV machine");
        assertTrue(
            defaultLimit.getRecipeEUt() <= defaultLimit.getMaxAllowedRecipeEUt(),
            "GT's default would have allowed it");
    }

    /**
     * A steam multiblock's cost multiplier scales its draw, and without overclocks the hatch voltage
     * changes nothing. The oracle is GregTech's no-overclock calculator.
     */
    @Test
    void noOverclockSpecMatchesGregTechsNoOverclockCalculator() {
        final ProcessingSpec steam = ProcessingSpec.builder()
            .speed(in -> 1.25)
            .euModifierNotLimitingParallel(in -> 2.5)
            .noOverclock()
            .build();

        final OverclockCalculator planned = GTSpecs.calculator(steam, state(9, 0), 16, 200, 0)
            .setParallel(8)
            .calculate();
        final OverclockCalculator gregtech = OverclockCalculator.ofNoOverclock(16, 200)
            .setDurationModifier(0.8)
            .setEUtDiscount(2.5)
            .setParallel(8)
            .calculate();

        assertEquals(gregtech.getDuration(), planned.getDuration());
        assertEquals(gregtech.getConsumption(), planned.getConsumption());
    }
}
