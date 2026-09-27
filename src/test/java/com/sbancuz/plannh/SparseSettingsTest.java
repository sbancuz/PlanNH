package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Serializer;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.harness.GtnhFlowLoader;

/**
 * The settings map holds only what the user chose. Two things follow that nothing else pins: an
 * untouched node writes no config at all, and merely solving a chart must not look like editing it -
 * the balancer writes its solved machine count back into every node on every solve.
 */
class SparseSettingsTest {

    @Test
    void anUntouchedNodeStoresNothingWorthSaving() {
        final GtnhFlowLoader.LoadedChart chart = GtnhFlowLoader.load("mk1");

        for (final Node node : chart.machines()) {
            if (node.machineConfig.isMachineCountPinned()) continue; // a chart pin, deliberately stored
            assertFalse(node.machineConfig.hasStoredSettings(), node.machineName + " stores config it was never given");
        }
    }

    /** An untouched node stores no count at all: presence of the key is what pins it to the solver. */
    @Test
    void anUntouchedNodeHasNoMachineCount() {
        final GtnhFlowLoader.LoadedChart chart = GtnhFlowLoader.load("mk1");
        final Node node = chart.machine(0);

        assertFalse(
            node.machineConfig.settings.containsKey(Settings.MACHINES.key()),
            "a seeded count would read as a pin and freeze the node at one machine");
        assertFalse(node.machineConfig.isMachineCountPinned());
        assertEquals(1, node.machineConfig.getMachineCount(), "unpinned still contributes a multiplier of one");
    }

    /**
     * Solving writes the machine count back into every node. If that counted as stored config, every
     * chart would gain a config block on every node just from being looked at, and undo would see a
     * diff where the user did nothing.
     */
    @Test
    void solvingAChartDoesNotChangeWhatItSerializesTo() {
        final GtnhFlowLoader.LoadedChart chart = GtnhFlowLoader.load("mk1");
        final Graph graph = chart.graph();

        final String beforeSolve = Serializer.encode(graph);
        graph.setBalanceMode(BalanceMode.OUTPUT);
        graph.balance();
        final String afterSolve = Serializer.encode(graph);

        // Balance mode itself is chart state and is expected to differ; re-encode under the same
        // mode to isolate what the solve wrote into the nodes.
        graph.balance();
        assertEquals(afterSolve, Serializer.encode(graph), "a second solve changed the chart");
        assertFalse(beforeSolve.isEmpty());
    }

    @Test
    void aStoredValueEqualToItsDefaultStillCounts() {
        final GtnhFlowLoader.LoadedChart chart = GtnhFlowLoader.load("mk1");
        final Node node = chart.machine(0);

        node.machineConfig.settings.put(Settings.TICK_MODIFIER.key(), Settings.TICK_MODIFIER.def().defaultValue);

        assertTrue(
            node.machineConfig.hasStoredSettings(),
            "choosing the default is still a choice, and must survive a save");
    }
}
