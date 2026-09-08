package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.GraphData;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.MachineGroup;
import com.sbancuz.plannh.data.flowchart.Node;

/**
 * The chart as the solver sees it, built ONCE per run: machines in recipe-extent form, ports
 * interned per machine and direction, the drawn edges, gates over the connected components of ports
 * and the packed per-gate weights. Everything a stage reads from here is stable for the whole solve;
 * the per-run pins and the point arrays live on {@link SolveContext}. Distinguishing the "dataset"
 * from the "point" is what lets the stage pipeline thread state without rebuilding the chart per
 * stage.
 */
public final class ModelData {

    /**
     * A machine as the solver sees it: the node plus its per-port rates in recipe-extent form.
     */
    // TODO: remove this when with the Step pr using the unified interface
    public static final class Machine {

        final Node node;
        public final int durTicks;

        /**
         * The underlying chart node, for identity lookups (e.g. the alternatives resolver).
         */
        public Node node() {
            return node;
        }

        final double[] inQty;
        final double[] outQty;
        /**
         * Extent implied by the node's target output rates, or zero when there is none.
         */
        final double targetExtent;
        /**
         * Extent implied by the node's fixed machine count, or null when unfixed.
         */
        final @Nullable Double fixedExtent;

        private Machine(final Node node) {
            this.node = node;
            final MachineConfig cfg = node.getMachineConfig();
            final var eff = cfg.computeEffect(node.getProperties());
            this.durTicks = Math.max(1, eff.durationTicks());
            final int tf = eff.throughputFactor();
            this.inQty = new double[node.getInputs()
                .size()];
            for (int i = 0; i < inQty.length; i++) {
                final var s = node.getInputs()
                    .get(i);
                inQty[i] = Math.max(0, s.getAmount()) * s.getChance() * cfg.inputMultiplier(i) * tf;
            }
            this.outQty = new double[node.getOutputs()
                .size()];
            for (int i = 0; i < outQty.length; i++) {
                final var s = node.getOutputs()
                    .get(i);
                outQty[i] = Math.max(0, s.getAmount()) * s.getChance() * cfg.outputMultiplier(i) * tf;
            }
            this.targetExtent = targetExtent(node, outQty);
            this.fixedExtent = node.isMachineCountFixed() ? node.getMachineConfig()
                .getMachineCount() * (double) Numerics.TICKS_PER_SECOND
                / durTicks : null;
        }

        boolean hasPort(final int portIndex, final boolean input) {
            return portIndex >= 0 && portIndex < (input ? inQty.length : outQty.length);
        }

        double qty(final int portIndex, final boolean input) {
            return input ? inQty[portIndex] : outQty[portIndex];
        }

        /**
         * The extent implied by the node's target output rates: rate divided by per-craft quantity,
         * largest target winning (parallel outputs share one extent, so only the tightest can be hit
         * exactly). Targets on stale ports are skipped the same way stale edges are.
         */
        private static double targetExtent(final Node node, final double[] outQty) {
            double extent = 0;
            for (final Map.Entry<Integer, Double> t : node.getTargetOutputRates()
                .entrySet()) {
                if (t.getValue() == null || t.getValue() <= 0) continue;
                final int i = t.getKey();
                if (i < 0 || i >= outQty.length || outQty[i] <= 0) continue;
                extent = Math.max(extent, t.getValue() / outQty[i]);
            }
            return extent > 0 ? extent : 0;
        }
    }

    public record EdgeData(UUID id, int srcPort, int dstPort) {}

    public record ConnectedPort(int machine, int portIndex, boolean input, double qtyPerCraft, List<Integer> edges) {}

    /**
     * One gate: an ingredient component in one direction, covering the listed ports.
     */
    public record Gate(boolean input, List<Integer> ports) {}

    /**
     * One machine-sharing group with a capacity: the machines that run on the same hardware and how
     * many machines that hardware is. Only groups the player capped are built - a sharing group
     * without a capacity constrains nothing, so it never reaches the model.
     */
    public record Pool(List<Integer> machines, int capacity) {}

    public final List<Machine> machines = new ArrayList<>();
    public final Map<UUID, Integer> machineIndex = new HashMap<>();
    public final List<EdgeData> edges = new ArrayList<>();
    public final List<ConnectedPort> connectedPorts = new ArrayList<>();
    public final Map<Long, Integer> portLookup = new HashMap<>();
    public final List<Gate> gates = new ArrayList<>();
    public final List<Pool> pools = new ArrayList<>();
    public int[] portGate;
    public int[] portComponent;
    /**
     * Packed lexicographic per-gate weights from the type's heuristics.
     */
    final double[] gateWeights;

    ModelData(final Graph graph, final Heuristics heuristics) {
        for (final Node node : graph.getNodes()
            .values()) {
            if (node.getExtractor() == null) continue; // incorrectly loaded node
            machineIndex.put(node.getId(), machines.size());
            machines.add(new Machine(node));
        }

        for (final Edge edge : graph.getEdges()
            .values()) {
            final Integer src = machineIndex.get(edge.sourceNodeId);
            final Integer dst = machineIndex.get(edge.targetNodeId);
            if (src == null || dst == null) continue;
            // Edges keep the port indices they were saved with, while port lists can shrink (a
            // settings change, or a recipe that lost an output between modpack versions). Drop the
            // dangling edge rather than indexing past the node's ports.
            if (!machines.get(src)
                .hasPort(edge.sourceOutputIndex, false)
                || !machines.get(dst)
                    .hasPort(edge.targetInputIndex, true)) {
                continue;
            }
            final int srcPort = internPort(src, edge.sourceOutputIndex, false);
            final int dstPort = internPort(dst, edge.targetInputIndex, true);
            final int e = edges.size();
            edges.add(new EdgeData(edge.id, srcPort, dstPort));
            connectedPorts.get(srcPort)
                .edges()
                .add(e);
            connectedPorts.get(dstPort)
                .edges()
                .add(e);
        }

        buildPools(graph);
        buildGates();
        final boolean[] gateInput = new boolean[gates.size()];
        for (int g = 0; g < gates.size(); g++) {
            gateInput[g] = gates.get(g)
                .input();
        }
        gateWeights = heuristics.gateWeights(gates.size(), gateInput);
    }

    /**
     * The capped machine-sharing groups, as machine indices. A group holds node ids by geometry, so
     * a node that has since left the chart is skipped, and a group left with nothing to constrain
     * (no machines, or a capacity of zero) is not a pool at all.
     */
    private void buildPools(final Graph graph) {
        for (final Group group : graph.getGroups().values()) {
            if (!(group instanceof final MachineGroup machineGroup) || machineGroup.getMachineCapacity() <= 0) continue;
            final List<Integer> members = new ArrayList<>();
            for (final Map.Entry<UUID, GraphData> entry : group.getChildren().entrySet()) {
                if (entry.getValue() instanceof Node) {
                    UUID nodeId = entry.getKey();
                    final Integer m = machineIndex.get(nodeId);
                    if (m != null) members.add(m);
                }
            }
            if (members.isEmpty()) continue;
            pools.add(new Pool(List.copyOf(members), machineGroup.getMachineCapacity()));
        }
    }

    /**
     * Ingredient identity is structural: connected components of ports under the drawn edges. One
     * gate covers a component's input ports, one its output ports - so the binary count is ~2x
     * intermediates and not one per port.
     */
    private void buildGates() {
        final int n = connectedPorts.size();
        final int[] root = new int[n];
        for (int i = 0; i < n; i++) {
            root[i] = i;
        }
        for (final EdgeData e : edges) {
            union(root, e.srcPort(), e.dstPort());
        }
        portGate = new int[n];
        portComponent = new int[n];
        final Map<Long, Integer> gateLookup = new HashMap<>();
        for (int p = 0; p < n; p++) {
            final boolean input = connectedPorts.get(p)
                .input();
            portComponent[p] = find(root, p);
            final long key = ((long) portComponent[p] << 1) | (input ? 1 : 0);
            Integer gate = gateLookup.get(key);
            if (gate == null) {
                gates.add(new Gate(input, new ArrayList<>()));
                gate = gates.size() - 1;
                gateLookup.put(key, gate);
            }
            gates.get(gate)
                .ports()
                .add(p);
            portGate[p] = gate;
        }
    }

    private int internPort(final int machine, final int portIndex, final boolean input) {
        final long key = ((long) machine << 32) | ((long) portIndex << 1) | (input ? 1 : 0);
        return portLookup.computeIfAbsent(key, k -> {
            connectedPorts.add(
                new ConnectedPort(
                    machine,
                    portIndex,
                    input,
                    machines.get(machine)
                        .qty(portIndex, input),
                    new ArrayList<>()));
            return connectedPorts.size() - 1;
        });
    }

    private static int find(final int[] root, final int i) {
        int r = i;
        while (root[r] != r) {
            r = root[r];
        }
        root[i] = r;
        return r;
    }

    private static void union(final int[] root, final int a, final int b) {
        root[find(root, a)] = find(root, b);
    }
}
