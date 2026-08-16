package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Serializer;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;
import com.sbancuz.plannh.data.provider.gregtech.GTStructureTiers;

import gregtech.api.enums.GTValues;

/**
 * The structure a chart plans with. It decides what every untouched node in that chart opens on, so
 * it has to survive a save, and the recipe-driven part of it has to land on a coil that can actually
 * run the recipe.
 */
class ChartMinimumsTest {

    /** A chart that has said nothing plans at the best the game offers, which is today's behaviour. */
    @Test
    void aFreshChartHasNoMinimums() {
        final Graph graph = new Graph("Slot 1");

        assertEquals(Graph.NO_MINIMUM, graph.getMinCoilTier());
        assertEquals(Graph.NO_MINIMUM, graph.getMinPipeCasingTier());
        assertEquals(Graph.NO_MINIMUM, graph.getMinVoltageTier());
    }

    @Test
    void everyMinimumSurvivesASave() {
        final Plan plan = Plan.createEmpty();
        final Graph graph = plan.getGraphs()
            .getFirst();
        graph.setMinCoilTier(3);
        graph.setMinPipeCasingTier(2);
        graph.setMinVoltageTier(5);

        final Graph decoded = Serializer.decodePlan(Serializer.encodePlan(plan))
            .getGraphs()
            .getFirst();

        assertEquals(3, decoded.getMinCoilTier());
        assertEquals(2, decoded.getMinPipeCasingTier());
        assertEquals(5, decoded.getMinVoltageTier());
    }

    /** Charts saved before minimums existed carry none, and must open the way they always did. */
    @Test
    void aSaveWithoutTheKeysKeepsTheDefaults() {
        final String json = Serializer.encodePlan(Plan.createEmpty())
            .replace("minCoilTier", "unusedKeyA")
            .replace("minPipeCasingTier", "unusedKeyB")
            .replace("minVoltageTier", "unusedKeyC");

        final Graph decoded = Serializer.decodePlan(json)
            .getGraphs()
            .getFirst();

        assertEquals(Graph.NO_MINIMUM, decoded.getMinCoilTier());
        assertEquals(Graph.NO_MINIMUM, decoded.getMinPipeCasingTier());
        assertEquals(Graph.NO_MINIMUM, decoded.getMinVoltageTier());
    }

    /**
     * A minimum changes what an untouched node runs at, so it changes that node's parallel count and
     * every rate downstream of it. A chart that did not re-solve would keep showing the old numbers.
     */
    @Test
    void settingAMinimumMakesTheChartResolveAgain() {
        final Graph graph = new Graph("Slot 1");
        final BalanceResult solved = graph.balance();
        assertSame(solved, graph.balance(), "an untouched chart keeps the solve it already has");

        graph.setMinCoilTier(0);

        assertNotSame(solved, graph.balance());
    }

    /** Zero and below is "no requirement", which is what a recipe with no heat carries. */
    @ParameterizedTest
    @ValueSource(ints = { -1, 0 })
    void aRecipeThatAsksForNoHeatAsksForNoCoil(final int heat) {
        assertEquals(0, GTSettings.coilTierForHeat(heat));
    }

    @Test
    void aRecipeHotterThanEveryCoilLandsOnTheHottest() {
        assertEquals(
            GTStructureTiers.MAX_COIL_TIER,
            GTSettings.coilTierForHeat(GTStructureTiers.coilHeat(GTStructureTiers.MAX_COIL_TIER) + 1));
    }

    /**
     * The property that matters, checked over the whole range rather than at a chosen tier: the coil
     * picked reaches the heat, and the one below it does not. One tier too low is a node that opens
     * on a structure its own recipe cannot run in.
     */
    @Test
    void everyHeatLandsOnTheWeakestCoilThatReachesIt() {
        for (int tier = 0; tier <= GTStructureTiers.MAX_COIL_TIER; tier++) {
            for (final int heat : new int[] { GTStructureTiers.coilHeat(tier) - 1, GTStructureTiers.coilHeat(tier) }) {
                if (heat <= 0) continue;
                final int picked = GTSettings.coilTierForHeat(heat);

                assertTrue(
                    GTStructureTiers.coilHeat(picked) >= heat,
                    "coil " + picked + " runs at " + GTStructureTiers.coilHeat(picked) + " for " + heat);
                assertTrue(
                    picked == 0 || GTStructureTiers.coilHeat(picked - 1) < heat,
                    "coil " + (picked - 1) + " already reached " + heat);
            }
        }
    }

    /**
     * A recipe the chart's own hatch cannot power still gets one that can. Outside a game there is no
     * chart to read, so this is the recipe's floor on its own - which is also what it must be for any
     * chart that has set no voltage.
     */
    @Test
    void aRecipeTooExpensiveForTheChartRaisesItsOwnNode() {
        final long hv = GTValues.V[3];

        assertEquals(3, GTSettings.defaultVoltageTier(hv));
        assertEquals(0, GTSettings.defaultVoltageTier(0));
    }
}
