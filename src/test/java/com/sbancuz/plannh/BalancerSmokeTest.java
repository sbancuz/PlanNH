package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;
import com.sbancuz.plannh.data.flowchart.balancer.SolverMessage;
import com.sbancuz.plannh.harness.GtnhFlowLoader;
import com.sbancuz.plannh.harness.GtnhFlowLoader.LoadedChart;

/**
 * Behavior of the balancer over the corpus: it must never crash, never exceed the per-solve
 * budget, and always return a usable result (the ILP falls back to configured counts when
 * infeasible). Accuracy against the corpus ground truths is covered by {@link GroundTruthTest}.
 */
class BalancerSmokeTest {

    // Target pins are AUTO's anchors; OUTPUT ignores them, so these solves run unanchored.
    // TODO: 30s is the relaxed beta figure; bring it back down as solve times allow.
    private static final Duration BUDGET = Duration.ofSeconds(30);

    static String[] corpus() {
        return GtnhFlowLoader.CORPUS;
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void noneModeUsesConfiguredCounts(final String name) {
        final LoadedChart chart = GtnhFlowLoader.load(name);
        final BalanceResult result = Balancer.balance(chart.graph(), BalanceMode.NONE, false);
        assertNotNull(result);
        for (final Node node : chart.machines()) {
            assertTrue(
                result.nodeBalances()
                    .containsKey(node.getId()),
                "missing balance for " + node.getMachineName());
        }
    }

    /**
     * AUTO mode through the {@link Balancer} entry point: always returns a usable result inside
     * the interactive budget (worst case: two floor passes of up to three stages each), with the
     * displayed operation count being the ceiling of the fractional machine count.
     */
    @ParameterizedTest
    @MethodSource("corpus")
    void autoModeStaysWithinBudget(final String name) {
        final LoadedChart chart = GtnhFlowLoader.load(name);
        final BalanceResult result = assertTimeoutPreemptively(
            Duration.ofSeconds(60),
            () -> Balancer.balance(chart.graph(), BalanceMode.AUTO, false),
            name + " exceeded the auto-balance budget");
        assertNotNull(result);
        // A failed solve also fills nodeBalances - with zeros - so presence alone would pass even
        // if AUTO never solved anything.
        assertTrue(result.totalOperations() > 0, () -> name + " produced no operations, notes: " + result.notes());
        for (final Node node : chart.machines()) {
            assertTrue(
                result.nodeBalances()
                    .containsKey(node.getId()),
                "missing balance for " + node.getMachineName());
        }
    }

    /**
     * AUTO must never write solved counts back into the node configs: viewing a chart is not
     * editing it. The reported operation count is the exact fractional machine count - no
     * rounding anywhere, so every displayed number can be checked against every other by hand.
     */
    @Test
    void autoModeReportsExactFractionalCounts_andDoesNotWriteThemBack() {
        final LoadedChart chart = GtnhFlowLoader.load("loopGraph");
        final Node lcr = chart.machine(1);
        assertFalse(lcr.isMachineCountFixed());
        lcr.getMachineConfig()
            .setMachineCount(3);

        final BalanceResult result = Balancer.balance(chart.graph(), BalanceMode.AUTO, false);

        assertEquals(
            3,
            lcr.getMachineConfig()
                .getMachineCount(),
            "configured count must survive viewing");
        assertEquals(
            8.0 / 15.0,
            result.nodeBalances()
                .get(lcr.getId())
                .operations(),
            1e-6,
            "the exact fractional machine count, not a ceiling");
    }

    /**
     * OUTPUT solves in count space ({@code ExtentMinStage} builds a MILP over whole machine
     * counts), so its answer is buildable without a read-out ceil: every machine reads an exact
     * integer. When the chart is infeasible for OUTPUT the result falls back to the configured
     * counts, which are integers as well - either way a whole-machine answer.
     */
    @ParameterizedTest
    @MethodSource("corpus")
    void outputModeSolvesWholeMachineCounts(final String name) {
        final LoadedChart chart = GtnhFlowLoader.load(name);
        final BalanceResult result = Balancer.balance(chart.graph(), BalanceMode.OUTPUT, false);

        for (final Node node : chart.machines()) {
            final double ops = result.nodeBalances()
                .get(node.getId())
                .operations();
            assertEquals(
                ops,
                Math.rint(ops),
                1e-6,
                name + ": " + node.getMachineName() + " must show a whole machine count, got " + ops);
            assertTrue(ops >= 1, name + ": " + node.getMachineName() + " solved to " + ops + " machines");
        }
    }

    /**
     * The gtnh-flow contract at the Balancer level: an unpinned chart in AUTO mode shows NO
     * quantities - zero counts, empty effective rates (so no throughput rows and an empty
     * summary), and a note telling the user how to ask for a balance. mk1's only pin is its
     * target: rate, so clearing that leaves an unpinned chart.
     */
    @Test
    void autoModeUnpinned_showsNoQuantities() {
        final LoadedChart chart = GtnhFlowLoader.load("mk1");
        GtnhFlowLoader.clearTargetPins(chart);

        final BalanceResult result = Balancer.balance(chart.graph(), BalanceMode.AUTO, false);

        assertEquals(0.0, result.totalOperations(), 1e-9);
        for (final Node node : chart.machines()) {
            final var nb = result.nodeBalances()
                .get(node.getId());
            assertEquals(0.0, nb.operations(), 1e-9, node.getMachineName() + " must show no count");
            assertTrue(
                nb.effectiveOutputs()
                    .isEmpty()
                    && nb.effectiveInputs()
                        .isEmpty(),
                node.getMachineName() + " must show no rates");
        }
        assertTrue(
            result.notes()
                .stream()
                .anyMatch(n -> n.message() == SolverMessage.NO_PIN),
            "the summary must say how to ask for a balance, got: " + result.notes());
    }

    /**
     * Solver notes must reach the BalanceResult (and from there the summary widget): the
     * missing-edge diagnostic was useless while it only went to the log.
     */
    @Test
    void autoModeSurfacesMissingEdgeNotes() {
        final LoadedChart chart = GtnhFlowLoader.load("mk1_tiberium");
        GtnhFlowLoader.removeEdgesInto(chart, chart.machine(0), 0);

        final BalanceResult result = Balancer.balance(chart.graph(), BalanceMode.AUTO, false);

        assertTrue(
            result.notes()
                .stream()
                .anyMatch(
                    n -> n.message() == SolverMessage.WIRING_IMPORT || n.message() == SolverMessage.WIRING_UNLINKED),
            "the wiring diagnostic must reach the summary, got: " + result.notes());
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void outputModeStaysWithinBudget(final String name) {
        final LoadedChart chart = GtnhFlowLoader.load(name);
        final BalanceResult result = assertTimeoutPreemptively(
            BUDGET,
            () -> Balancer.balance(chart.graph(), BalanceMode.OUTPUT, false),
            name + " exceeded the " + BUDGET.toSeconds() + "s solve budget");
        assertNotNull(result);
        for (final Node node : chart.machines()) {
            final double ops = result.nodeBalances()
                .get(node.getId())
                .operations();
            assertTrue(ops >= 1, node.getMachineName() + " solved to " + ops + " machines");
        }
    }
}
