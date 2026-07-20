package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sbancuz.plannh.data.flowchart.Balancer;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.harness.ConservationValidator;
import com.sbancuz.plannh.harness.GtnhFlowLoader;
import com.sbancuz.plannh.harness.GtnhFlowLoader.LoadedChart;
import com.sbancuz.plannh.harness.TestIngredients.TestIngredient;

/**
 * Behavior of the balancer over the corpus: it must never crash, never exceed the 15s per-solve
 * budget, and always return a usable result (the ILP falls back to configured counts when
 * infeasible).
 */
class BalancerSmokeTest {

    private static final Duration BUDGET = Duration.ofSeconds(15);

    @ParameterizedTest
    @ValueSource(
        strings = { "mk1", "loopGraph", "light_fuel", "light_fuel_hydrogen_loop", "230_platline", "palladium_line",
            "nanocircuits" })
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

    /**
     * The summary is pure bookkeeping over the node balances: whatever counts the balancer
     * chose, each Products line must equal the summed per-ingredient surplus, each External
     * Inputs line the summed deficit, and no ingredient with a significant net flow may be
     * missing from the summary.
     */
    @ParameterizedTest
    @ValueSource(
        strings = { "mk1", "loopGraph", "light_fuel", "light_fuel_hydrogen_loop", "230_platline", "palladium_line",
            "nanocircuits" })
    void summaryMatchesNodeBalances(final String name) {
        final LoadedChart chart = GtnhFlowLoader.load(name);
        final BalanceResult result = Balancer.balance(chart.graph(), BalanceMode.OUTPUT, false);
        final Summary summary = Summary.compute(result, chart.graph(), false);
        final Map<String, ConservationValidator.Flows> flows = ConservationValidator.flows(chart, result);

        for (final Summary.Line<?> line : summary.outputs()) {
            final String ingredient = ((TestIngredient) line.resource()).name;
            final ConservationValidator.Flows f = flows.get(ingredient);
            assertNotNull(f, name + " summary invented an output: " + ingredient);
            assertEquals(f.net(), line.amount(), tolerance(f), name + " output line: " + ingredient);
        }
        for (final Summary.Line<?> line : summary.inputs()) {
            final String ingredient = ((TestIngredient) line.resource()).name;
            final ConservationValidator.Flows f = flows.get(ingredient);
            assertNotNull(f, name + " summary invented an input: " + ingredient);
            assertEquals(-f.net(), line.amount(), tolerance(f), name + " input line: " + ingredient);
        }
        for (final Map.Entry<String, ConservationValidator.Flows> e : flows.entrySet()) {
            final double net = e.getValue()
                .net();
            if (net > tolerance(e.getValue())) {
                assertTrue(
                    hasLine(summary.outputs(), e.getKey()),
                    name + " missing output line for " + e.getKey() + " (net " + net + ")");
            } else if (-net > tolerance(e.getValue())) {
                assertTrue(
                    hasLine(summary.inputs(), e.getKey()),
                    name + " missing input line for " + e.getKey() + " (net " + net + ")");
            }
        }
    }

    /**
     * A summary line is the difference of two float sums; when production nearly cancels
     * consumption at large magnitude, that difference is only accurate to a few float ulps of
     * the GROSS flow, so the tolerance must scale with gross, not net.
     */
    private static double tolerance(final ConservationValidator.Flows f) {
        return Math.max(1e-3, 1e-5 * (f.produced() + f.consumed()));
    }

    private static boolean hasLine(final List<Summary.Line<?>> lines, final String ingredient) {
        for (final Summary.Line<?> line : lines) {
            if (((TestIngredient) line.resource()).name.equals(ingredient)) return true;
        }
        return false;
    }

    @ParameterizedTest
    @ValueSource(
        strings = { "mk1", "loopGraph", "light_fuel", "light_fuel_hydrogen_loop", "230_platline", "palladium_line",
            "nanocircuits" })
    void outputModeStaysWithinBudget(final String name) {
        final LoadedChart chart = GtnhFlowLoader.load(name);
        final BalanceResult result = assertTimeoutPreemptively(
            BUDGET,
            () -> Balancer.balance(chart.graph(), BalanceMode.OUTPUT, false),
            name + " exceeded the 15s solve budget");
        assertNotNull(result);
        for (final Node node : chart.machines()) {
            final double ops = result.nodeBalances()
                .get(node.id)
                .operations();
            assertTrue(ops >= 1, node.machineName + " solved to " + ops + " machines");
        }
    }
}
