package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
        assertEquals(5, fusion.durationTicks);

        // The target pin fixes the count: 100 fuel per 5t = 400/s per machine, so 10/s needs
        // ceil(10 / 400) = 1 machine.
        assertTrue(fusion.isMachineCountFixed(), "target: pin must fix the machine count");
        assertEquals(1, fusion.machineConfig.getMachineCount());
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
        assertTrue(dt.isMachineCountFixed(), "number: pin must fix the machine count");
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
        for (final String name : new String[] { "mk1", "loopGraph", "light_fuel", "light_fuel_hydrogen_loop",
            "230_platline", "palladium_line", "nanocircuits" }) {
            final LoadedChart chart = GtnhFlowLoader.load(name);
            assertTrue(
                chart.graph()
                    .getEdges()
                    .size()
                    >= chart.machines()
                        .size() - 1,
                name + " should be connected-ish, got "
                    + chart.graph()
                        .getEdges()
                        .size()
                    + " edges");
        }
    }
}
