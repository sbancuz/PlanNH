package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.harness.GtnhFlowLoader;
import com.sbancuz.plannh.harness.GtnhFlowLoader.LoadedChart;
import com.sbancuz.plannh.harness.TestIngredients;

/**
 * The corpus loader must reproduce each chart's structure exactly: machine counts from the
 * source charts, pooled ingredients materialized as explicit edges, pins preserved.
 */
class GtnhFlowLoadTest {

    @Test
    void mk1Structure() {
        final LoadedChart chart = GtnhFlowLoader.load("mk1");
        assertEquals(
            2,
            chart.machines()
                .size());
        // DT feeds the fusion reactor heavy + light naquadah fuel.
        assertEquals(
            2,
            chart.graph()
                .getEdges()
                .size());
        assertEquals(
            1,
            chart.pins()
                .size());
        assertEquals(
            "target",
            chart.pins()
                .get(0)
                .kind());
        assertEquals(
            "naquadah fuel mk1",
            chart.pins()
                .get(0)
                .ingredient());
        assertEquals(
            10.0,
            chart.pins()
                .get(0)
                .value());

        // 0.25s fusion recipe -> 5 ticks.
        final Node fusion = chart.machine(0);
        assertEquals("fusion reactor", fusion.machineName);
        assertEquals(5, fusion.properties.get(GtnhFlowLoader.DURATION_TICKS));

        // The target pin lands on the node itself: output 0 is the fuel, pinned at 10/s. The
        // count stays free - AUTO derives the exact fractional extent from the rate.
        assertTrue(!fusion.machineConfig.isMachineCountPinned(), "a target: pin must not fix the machine count");
        assertEquals(10.0, fusion.targetOutputRates.get(0), 1e-9, "10/s on the fuel output");
    }

    @Test
    void loopGraphStructure() {
        final LoadedChart chart = GtnhFlowLoader.load("loopGraph");
        assertEquals(
            2,
            chart.machines()
                .size());
        // sulfuric acid DT -> LCR, diluted sulfuric acid LCR -> DT.
        assertEquals(
            2,
            chart.graph()
                .getEdges()
                .size());

        final Node dt = chart.machine(0);
        assertTrue(dt.machineConfig.isMachineCountPinned(), "number: pin must fix the machine count");
        assertEquals(1, dt.machineConfig.getMachineCount());
    }

    @Test
    void fractionalQuantitiesRoundTrip() {
        // palladium_line contains sub-1 per-craft quantities; the amount/chance encoding must
        // reproduce them exactly enough for ratio math (they are read back as amount * chance).
        final LoadedChart chart = GtnhFlowLoader.load("palladium_line");
        for (final Node node : chart.machines()) {
            node.inputs.forEach(p -> assertTrue(TestIngredients.quantityOf(p) > 0));
            node.outputs.forEach(p -> assertTrue(TestIngredients.quantityOf(p) > 0));
        }
    }

    @Test
    void chartSizes() {
        assertEquals(
            3,
            GtnhFlowLoader.load("light_fuel")
                .machines()
                .size());
        assertEquals(
            3,
            GtnhFlowLoader.load("light_fuel_hydrogen_loop")
                .machines()
                .size());
        assertEquals(
            28,
            GtnhFlowLoader.load("230_platline")
                .machines()
                .size());
        assertEquals(
            56,
            GtnhFlowLoader.load("palladium_line")
                .machines()
                .size());
        assertEquals(
            394,
            GtnhFlowLoader.load("nanocircuits")
                .machines()
                .size());
    }

    @Test
    void everyChartHasEdges() {
        // A spanning structure per island, not per chart: a fixture may be deliberately two
        // unconnected sub-charts (two_decisions is), and "machines - 1" would call that a loader
        // failure. Counting the islands keeps the check as strict as it was on every other chart.
        for (final String name : GtnhFlowLoader.CORPUS) {
            final LoadedChart chart = GtnhFlowLoader.load(name);
            final int machines = chart.machines()
                .size();
            final int edges = chart.graph()
                .getEdges()
                .size();
            final int islands = componentCount(chart);
            assertTrue(
                edges >= machines - islands,
                name + " should be wired up, got "
                    + edges
                    + " edges for "
                    + machines
                    + " machines in "
                    + islands
                    + " island(s)");
        }
    }

    /** Weakly-connected components of the loaded chart, by union-find over its edges. */
    private static int componentCount(final LoadedChart chart) {
        final Map<UUID, UUID> parent = new HashMap<>();
        for (final Node machine : chart.machines()) {
            parent.put(machine.id, machine.id);
        }
        for (final Edge edge : chart.graph()
            .getEdges()) {
            parent.put(find(parent, edge.sourceNodeId), find(parent, edge.targetNodeId));
        }
        final Set<UUID> roots = new HashSet<>();
        for (final UUID id : parent.keySet()) {
            roots.add(find(parent, id));
        }
        return roots.size();
    }

    private static UUID find(final Map<UUID, UUID> parent, final UUID id) {
        UUID root = id;
        while (!parent.get(root)
            .equals(root)) {
            root = parent.get(root);
        }
        return root;
    }
}
