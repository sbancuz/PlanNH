package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset;
import com.sbancuz.plannh.data.provider.gregtech.StructureState;
import com.sbancuz.plannh.data.provider.gregtech.probe.MachineProbe;
import com.sbancuz.plannh.data.provider.gregtech.probe.ProbeReading;

/**
 * Turning what a machine reported into a preset. The probe hands GregTech a recipe carrying values
 * no real recipe would, so anything that comes back changed was changed by the machine - that is how
 * a fixed heat floor or a rewritten recipe cost is told apart from one passed through untouched.
 */
class GTProbeReadingTest {

    private static final StructureState ANY = new StructureState(1, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    /** A machine that touched nothing: every number is the calculator's own default. */
    private static ProbeReading passthrough() {
        return new ProbeReading(
            1,
            1.0,
            1.0,
            4.0,
            2.0,
            1,
            false,
            false,
            0,
            MachineProbe.SENTINEL_HEAT,
            MachineProbe.SENTINEL_EUT,
            MachineProbe.SENTINEL_DURATION,
            false,
            false);
    }

    @Test
    void aPassthroughReadingClaimsNoHeatAndNoRecipeOverride() {
        final GTMachinePreset preset = MachineProbe.toPreset(passthrough());

        assertFalse(preset.usesHeat());
        assertEquals(GTMachinePreset.RECIPE_HEAT_FROM_RECIPE, preset.recipeHeatOverride());
        assertNull(preset.recipeOverride());
        assertFalse(preset.unlimitedTierSkips());
        // A machine that reported the calculator's own default said nothing, so the preset leaves it
        // unset and the applier never calls the setter - which lands on that same default.
        assertEquals(GTMachinePreset.TIER_SKIPS_UNSET, preset.maxTierSkips());
    }

    @Test
    void theHeadlineNumbersCarryStraightThrough() {
        final ProbeReading reading = new ProbeReading(
            48,
            1 / 2.25,
            0.9,
            4.0,
            4.0,
            1,
            false,
            false,
            0,
            MachineProbe.SENTINEL_HEAT,
            MachineProbe.SENTINEL_EUT,
            MachineProbe.SENTINEL_DURATION,
            false,
            false);
        final GTMachinePreset preset = MachineProbe.toPreset(reading);

        assertEquals(
            48,
            preset.maxParallel()
                .applyAsInt(ANY));
        assertEquals(
            1 / 2.25,
            preset.durationModifier()
                .applyAsDouble(ANY));
        assertEquals(
            0.9,
            preset.euModifier()
                .applyAsDouble(ANY));
        assertEquals(
            4.0,
            preset.durationDecreasePerOC()
                .applyAsDouble(ANY));
    }

    /** GT++'s Alloy Smelter pins its heat floor at 0 so it overclocks every 900K rather than 1800K. */
    @Test
    void aPinnedHeatFloorIsRecognisedAsAnOverride() {
        final ProbeReading reading = new ProbeReading(
            4,
            1.0,
            1.0,
            4.0,
            2.0,
            1,
            true,
            false,
            7202,
            0,
            MachineProbe.SENTINEL_EUT,
            MachineProbe.SENTINEL_DURATION,
            false,
            false);
        final GTMachinePreset preset = MachineProbe.toPreset(reading);

        assertTrue(preset.heatOC());
        assertTrue(preset.usesHeat());
        assertEquals(0, preset.recipeHeatOverride());
        assertNotEquals(GTMachinePreset.RECIPE_HEAT_FROM_RECIPE, preset.recipeHeatOverride());
        assertEquals(
            7202,
            preset.machineHeat()
                .applyAsInt(ANY));
    }

    /** A machine that discounts on heat without overclocking on it still needs its heat carried. */
    @Test
    void heatDiscountAloneStillReportsTheMachineHeat() {
        final ProbeReading reading = new ProbeReading(
            1,
            1.0,
            1.0,
            4.0,
            2.0,
            1,
            false,
            true,
            3601,
            MachineProbe.SENTINEL_HEAT,
            MachineProbe.SENTINEL_EUT,
            MachineProbe.SENTINEL_DURATION,
            false,
            false);
        final GTMachinePreset preset = MachineProbe.toPreset(reading);

        assertFalse(preset.heatOC());
        assertTrue(preset.usesHeat());
        assertEquals(
            3601,
            preset.machineHeat()
                .applyAsInt(ANY));
    }

    /** The Industrial Arc Furnace forbids tier skipping outright, which is not the same as unset. */
    @Test
    void aDeliberateTierSkipCountIsKept() {
        final ProbeReading reading = new ProbeReading(
            1,
            1.0,
            1.0,
            4.0,
            2.0,
            0,
            false,
            false,
            0,
            MachineProbe.SENTINEL_HEAT,
            MachineProbe.SENTINEL_EUT,
            MachineProbe.SENTINEL_DURATION,
            false,
            false);

        assertEquals(
            0,
            MachineProbe.toPreset(reading)
                .maxTierSkips());
    }

    @Test
    void anIntegerMaxTierSkipReadsAsUnlimitedRatherThanAsACount() {
        final ProbeReading reading = new ProbeReading(
            1,
            1.0,
            1.0,
            4.0,
            2.0,
            Integer.MAX_VALUE,
            false,
            false,
            0,
            MachineProbe.SENTINEL_HEAT,
            MachineProbe.SENTINEL_EUT,
            MachineProbe.SENTINEL_DURATION,
            false,
            false);
        final GTMachinePreset preset = MachineProbe.toPreset(reading);

        assertTrue(preset.unlimitedTierSkips());
        assertNotEquals(Integer.MAX_VALUE, preset.maxTierSkips());
    }

    @Test
    void aRewrittenRecipeCostIsRecognisedAsAnOverride() {
        final ProbeReading reading = new ProbeReading(
            1,
            1.0,
            1.0,
            4.0,
            2.0,
            1,
            false,
            false,
            0,
            MachineProbe.SENTINEL_HEAT,
            4,
            128,
            false,
            false);
        final GTMachinePreset preset = MachineProbe.toPreset(reading);

        assertEquals(new GTMachinePreset.RecipeOverride(4, 128), preset.recipeOverride());
    }

    /** A machine reporting no parallel at all would silently zero a chart's throughput. */
    @Test
    void parallelIsFlooredAtOne() {
        final ProbeReading reading = new ProbeReading(
            0,
            1.0,
            1.0,
            4.0,
            2.0,
            1,
            false,
            false,
            0,
            MachineProbe.SENTINEL_HEAT,
            MachineProbe.SENTINEL_EUT,
            MachineProbe.SENTINEL_DURATION,
            false,
            false);

        assertEquals(
            1,
            MachineProbe.toPreset(reading)
                .maxParallel()
                .applyAsInt(ANY));
    }
}
