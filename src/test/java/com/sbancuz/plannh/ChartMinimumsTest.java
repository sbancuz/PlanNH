package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Serializer;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;

import gregtech.api.enums.GTValues;

/**
 * The structure tiers a chart is planned with. They set the value every untouched node in the chart
 * opens on, so they must survive a save, and the recipe-driven floor must land on a coil that can run
 * the recipe.
 */
class ChartMinimumsTest {

    private static final String COIL = Settings.GT_COIL.key();
    private static final String PIPE_CASING = Settings.GT_PIPE_CASING.key();
    private static final String VOLTAGE = Settings.VOLTAGE.key();

    /** A chart with no minimums set is planned at the best the game offers. */
    @Test
    void aFreshChartHasNoMinimums() {
        final Graph graph = new Graph("Slot 1");

        assertTrue(
            graph.getMinimums()
                .isEmpty());
        assertEquals(Graph.NO_MINIMUM, graph.getMinimum(COIL));
        assertEquals(Graph.NO_MINIMUM, graph.getMinimum(PIPE_CASING));
        assertEquals(Graph.NO_MINIMUM, graph.getMinimum(VOLTAGE));
    }

    @Test
    void everyMinimumSurvivesASave() {
        final Graph graph = new Graph("Slot 1");
        graph.setMinimum(COIL, 3);
        graph.setMinimum(PIPE_CASING, 2);
        graph.setMinimum(VOLTAGE, 5);

        final Graph decoded = Serializer.decode(Serializer.encode(graph));

        assertEquals(3, decoded.getMinimum(COIL));
        assertEquals(2, decoded.getMinimum(PIPE_CASING));
        assertEquals(5, decoded.getMinimum(VOLTAGE));
    }

    /** Charts saved before minimums existed have no minimum keys, and must decode with none set. */
    @Test
    void aSaveWithoutTheKeysKeepsTheDefaults() {
        final Graph decoded = Serializer.decode(Serializer.encode(new Graph("Slot 1")));

        assertTrue(
            decoded.getMinimums()
                .isEmpty(),
            "a chart that set nothing must not come back holding sentinels");
        assertEquals(Graph.NO_MINIMUM, decoded.getMinimum(COIL));
    }

    /**
     * A minimum changes an untouched node's tier, so it changes that node's parallel count and every
     * rate downstream of it. Without a re-solve the chart would print stale numbers.
     */
    @Test
    void settingAMinimumMakesTheChartResolveAgain() {
        final Graph graph = new Graph("Slot 1");
        final BalanceResult solved = graph.balance();
        assertSame(solved, graph.balance(), "an untouched chart keeps the solve it already has");

        graph.setMinimum(COIL, 0);

        assertNotSame(solved, graph.balance());
    }

    /**
     * A recipe the chart's hatch can't power still gets a voltage that can. Outside a game there is no
     * chart to read, so this tests the recipe's floor alone, which is also the result for a chart with
     * no voltage set.
     */
    @Test
    void aRecipeTooExpensiveForTheChartRaisesItsOwnNode() {
        final long hv = GTValues.V[3];

        assertEquals(3, GTSettings.defaultVoltageTier(hv));
        assertEquals(0, GTSettings.defaultVoltageTier(0));
    }
}
