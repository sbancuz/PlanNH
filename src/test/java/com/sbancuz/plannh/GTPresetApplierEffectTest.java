package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.provider.gregtech.GTPresetApplier;

import gregtech.api.logic.ProcessingRun;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;

/** What a node reads from a GregTech run besides duration, EU/t and parallels. */
class GTPresetApplierEffectTest {

    /** Modelled on the Eye of Harmony: each parallel may fail, a success yields less, and EU flows once per run. */
    @Test
    void aRunsOddsScaleItsOutputsAndItsOnceOffEuIsStated() {
        final ProcessingRun run = new ProcessingRun(
            CheckRecipeResultRegistry.SUCCESSFUL,
            4,
            0,
            100,
            0,
            new ProcessingRun.RunEu(BigInteger.ZERO, BigInteger.valueOf(1000), BigInteger.TEN),
            new ProcessingRun.Output(0.5, 0.8));

        final EffectResult effect = GTPresetApplier.effect(run, 2);

        assertEquals(8, effect.throughputFactor());
        assertEquals(0.4, effect.outputFactor(), 1e-9);
        assertEquals(
            3,
            effect.details()
                .size(),
            "odds, EU taken per run and EU given per run");
    }

    @Test
    void aPlainRunAddsNothing() {
        final ProcessingRun run = new ProcessingRun(
            CheckRecipeResultRegistry.SUCCESSFUL,
            1,
            2,
            50,
            120,
            ProcessingRun.RunEu.NONE,
            ProcessingRun.Output.CERTAIN);

        final EffectResult effect = GTPresetApplier.effect(run, 1);

        assertEquals(1, effect.outputFactor());
        assertTrue(
            effect.details()
                .isEmpty());
    }
}
