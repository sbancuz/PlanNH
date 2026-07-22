package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.sbancuz.plannh.data.flowchart.Balancer;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.harness.GtnhFlowLoader;
import com.sbancuz.plannh.harness.GtnhFlowLoader.LoadedChart;

/**
 * Behavior of the balancer over the corpus: it must never crash, never exceed the per-solve
 * budget, and always return a usable result (the ILP falls back to configured counts when
 * infeasible).
 */
class BalancerSmokeTest {

    // KNOWN RED: target pins anchor the sink counts (nanocircuits: 400 asslines for 1/s), and
    // the current solver needs ~19s for that chart against this budget (6.7s un-anchored). The
    // budget is the requirement, not the variable - the failure stands until the solver closes
    // the gap.
    private static final Duration BUDGET = Duration.ofSeconds(15);

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
                    .containsKey(node.id),
                "missing balance for " + node.machineName);
        }
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
                .get(node.id)
                .operations();
            assertTrue(ops >= 1, node.machineName + " solved to " + ops + " machines");
        }
    }
}
