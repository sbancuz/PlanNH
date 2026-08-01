package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeProperty;

public final class Balancer {

    public enum BalanceMode {
        NONE,
        FORWARD,
        BACKWARD
    }

    @Nonnull
    public static BalanceResult balance(final Graph graph, final BalanceMode mode) {
        return switch (mode) {
            case NONE -> balanceNone(graph);
            case FORWARD -> balanceForward(graph);
            case BACKWARD -> balanceBackward(graph);
        };
    }

    /**
     * Builds outEdges/inEdges maps for every FlowData participant (nodes + sinks),
     * so that edge endpoints are never missing from the map.
     */
    @Nonnull
    private static EdgeMaps buildEdgeMaps(final Graph graph) {
        final Map<UUID, List<Edge>> outEdges = new HashMap<>();
        final Map<UUID, List<Edge>> inEdges = new HashMap<>();
        for (final FlowData fd : graph.getFlowParticipants()) {
            final UUID id = fd instanceof Node n ? n.id : ((Step) fd).getId();
            outEdges.put(id, new ArrayList<>());
            inEdges.put(id, new ArrayList<>());
        }
        for (final Edge edge : graph.getEdges()
            .values()) {
            final List<Edge> out = outEdges.get(edge.sourceId);
            final List<Edge> in = inEdges.get(edge.targetId);
            if (out != null) out.add(edge);
            if (in != null) in.add(edge);
        }
        return new EdgeMaps(outEdges, inEdges);
    }

    private record EdgeMaps(Map<UUID, List<Edge>> outEdges, Map<UUID, List<Edge>> inEdges) {}

    @Nonnull
    private static BalanceResult balanceNone(final Graph graph) {
        final Map<UUID, Integer> ops = new HashMap<>();
        for (final Node node : graph.getNodes()
            .values()) {
            ops.put(node.id, 1);
        }
        return buildResult(graph, ops);
    }

    @Nonnull
    private static BalanceResult balanceForward(final Graph graph) {
        final EdgeMaps edgeMaps = buildEdgeMaps(graph);
        final Map<UUID, List<Edge>> outEdges = edgeMaps.outEdges;
        final Map<UUID, List<Edge>> inEdges = edgeMaps.inEdges;

        final List<UUID> topoOrder = topologicalSort(graph, inEdges);
        if (topoOrder == null) {
            return balanceNone(graph);
        }

        final Map<UUID, Integer> ops = new HashMap<>();
        for (final Node node : graph.getNodes()
            .values()) {
            ops.put(
                node.id,
                inEdges.get(node.id)
                    .isEmpty() ? 1 : 0);
        }

        final Map<UUID, Integer> throughputFactors = new HashMap<>();
        for (final Node node : graph.getNodes()
            .values()) {
            final MachineConfig cfg = node.machineConfig;
            final var eff = cfg.computeEffect(node.properties, node.durationTicks);
            throughputFactors.put(node.id, eff.throughputFactor());
        }

        for (final UUID nodeId : topoOrder) {
            final Node node = graph.getNodes()
                .get(nodeId);
            if (node == null) continue;

            final int currentOps = ops.get(nodeId);
            if (currentOps <= 0) continue;

            for (final Edge edge : outEdges.get(nodeId)) {
                final Step targetStep = graph.getSteps()
                    .get(edge.targetId);
                if (targetStep != null) {
                    // Sink step: its per-second input demand forces this producer up.
                    final float demandPerSec = stepDemand(targetStep);
                    if (demandPerSec <= 0) continue;
                    final int needed = opsToMeetStepDemand(node, edge, throughputFactors.get(nodeId), demandPerSec);
                    if (needed > currentOps) ops.put(nodeId, needed);
                    continue;
                }

                final Node target = graph.getNodes()
                    .get(edge.targetId);
                if (target == null) continue;

                final int myOutputCount = node.outputs.get(edge.sourceOutputIndex)
                    .getAmount();
                if (myOutputCount <= 0) continue;
                final float outputChance = node.outputs.get(edge.sourceOutputIndex)
                    .getChance();

                final int targetInputCount = target.inputs.get(edge.targetInputIndex)
                    .getAmount();
                if (targetInputCount <= 0) continue;
                final float inputChance = target.inputs.get(edge.targetInputIndex)
                    .getChance();

                final MachineConfig cfg = node.machineConfig;
                final MachineConfig tgtCfg = target.machineConfig;
                final int srcThroughput = throughputFactors.get(nodeId);
                final int tgtThroughput = throughputFactors.get(edge.targetId);

                final float yield = currentOps * myOutputCount
                    * outputChance
                    * cfg.outputMultiplier(edge.sourceOutputIndex)
                    * srcThroughput;

                final float demandPerOp = targetInputCount * inputChance
                    * tgtCfg.inputMultiplier(edge.targetInputIndex)
                    * tgtThroughput;

                if (demandPerOp <= 0) continue;

                final int needed = (int) Math.ceil(yield / demandPerOp);
                final int existing = ops.get(edge.targetId);
                if (needed > existing) {
                    ops.put(edge.targetId, needed);
                }
            }
        }

        for (final Node node : graph.getNodes()
            .values()) {
            final int v = ops.get(node.id);
            if (v <= 0) ops.put(node.id, 1);
        }

        return buildResult(graph, ops);
    }

    @Nonnull
    private static BalanceResult balanceBackward(final Graph graph) {
        final EdgeMaps edgeMaps = buildEdgeMaps(graph);
        final Map<UUID, List<Edge>> outEdges = edgeMaps.outEdges;
        final Map<UUID, List<Edge>> inEdges = edgeMaps.inEdges;

        final Set<UUID> leafNodes = new HashSet<>();
        for (final Node node : graph.getNodes()
            .values()) {
            final boolean hasNodeOut = outEdges.get(node.id)
                .stream()
                .anyMatch(
                    e -> graph.getNodes()
                        .containsKey(e.targetId));
            if (!hasNodeOut) {
                leafNodes.add(node.id);
            }
        }

        final List<UUID> topoOrder = topologicalSort(graph, inEdges);
        if (topoOrder == null) {
            return fallbackBalance(graph);
        }

        final List<UUID> reverseTopo = new ArrayList<>(topoOrder);
        Collections.reverse(reverseTopo);

        final Map<UUID, Integer> ops = new HashMap<>();
        for (final Node node : graph.getNodes()
            .values()) {
            ops.put(node.id, leafNodes.contains(node.id) ? 1 : 0);
        }

        final Map<UUID, Integer> throughputFactors = new HashMap<>();
        for (final Node node : graph.getNodes()
            .values()) {
            final MachineConfig cfg = node.machineConfig;
            final var eff = cfg.computeEffect(node.properties, node.durationTicks);
            throughputFactors.put(node.id, eff.throughputFactor());
        }

        for (final UUID nodeId : reverseTopo) {
            final Node node = graph.getNodes()
                .get(nodeId);
            if (node == null) continue;

            final int currentOps = ops.get(nodeId);

            final Map<Integer, Float> itemsNeededPerPort = new HashMap<>();
            final Map<Integer, Float> yieldPerPort = new HashMap<>();
            int maxDemand = 0;
            for (final Edge edge : outEdges.get(nodeId)) {
                final Step targetStep = graph.getSteps()
                    .get(edge.targetId);
                if (targetStep != null) {
                    // Sink step: its per-second input demand forces this producer up.
                    final float demandPerSec = stepDemand(targetStep);
                    if (demandPerSec > 0) {
                        maxDemand = Math.max(
                            maxDemand,
                            opsToMeetStepDemand(node, edge, throughputFactors.get(nodeId), demandPerSec));
                    }
                    continue;
                }

                final Node target = graph.getNodes()
                    .get(edge.targetId);
                if (target == null) continue;

                final int targetOps = ops.get(edge.targetId);
                if (targetOps <= 0) continue;

                final int targetInputCount = target.inputs.get(edge.targetInputIndex)
                    .getAmount();
                if (targetInputCount <= 0) continue;
                final float inputChance = target.inputs.get(edge.targetInputIndex)
                    .getChance();

                final int myOutputCount = node.outputs.get(edge.sourceOutputIndex)
                    .getAmount();
                if (myOutputCount <= 0) continue;
                final float outputChance = node.outputs.get(edge.sourceOutputIndex)
                    .getChance();

                final MachineConfig cfg = node.machineConfig;
                final MachineConfig tgtCfg = target.machineConfig;
                final int srcThroughput = throughputFactors.get(nodeId);
                final int tgtThroughput = throughputFactors.get(edge.targetId);

                final float yield = myOutputCount * outputChance
                    * cfg.outputMultiplier(edge.sourceOutputIndex)
                    * srcThroughput;
                if (yield <= 0) continue;

                // A source step feeding this target's input port covers part of its
                // demand, so the upstream producer does not need to provide it all.
                final float stepSupply = stepSupplyPerSec(graph, inEdges, edge.targetId, edge.targetInputIndex);
                final float itemsNeeded = targetOps * targetInputCount
                    * inputChance
                    * tgtCfg.inputMultiplier(edge.targetInputIndex)
                    * tgtThroughput - stepSupply * targetOps * target.secondsPerCycle();
                if (itemsNeeded <= 0) continue;

                final int port = edge.sourceOutputIndex;
                itemsNeededPerPort.merge(port, itemsNeeded, Float::sum);
                yieldPerPort.put(port, yield);
            }

            for (final Map.Entry<Integer, Float> entry : itemsNeededPerPort.entrySet()) {
                final int port = entry.getKey();
                final float itemsNeeded = entry.getValue();
                final float yield = yieldPerPort.get(port);
                final int needed = (int) Math.ceil(itemsNeeded / yield);
                if (needed > maxDemand) maxDemand = needed;
            }

            if (maxDemand > currentOps) {
                ops.put(nodeId, maxDemand);
            } else if (currentOps == 0) {
                ops.put(nodeId, 1);
            }
        }

        return buildResult(graph, ops);
    }

    /**
     * Per-second input demand of a sink step (its input port amount). Returns 0
     * for source steps, whose input port carries no amount.
     */
    private static float stepDemand(final Step step) {
        final List<Port<?>> inputs = step.getInputs();
        return inputs.isEmpty() ? 0f
            : inputs.getFirst()
                .getAmount();
    }

    /**
     * Operator count the given producer needs so its output at the edge's source
     * port produces at least {@code demandPerSec} items per second.
     */
    private static int opsToMeetStepDemand(final Node producer, final Edge edge, final int throughputFactor,
        final float demandPerSec) {
        if (demandPerSec <= 0) return 0;
        final Port<?> out = producer.outputs.get(edge.sourceOutputIndex);
        final int outCount = out.getAmount();
        if (outCount <= 0) return 0;
        final float yield = outCount * out.getChance()
            * producer.machineConfig.outputMultiplier(edge.sourceOutputIndex)
            * throughputFactor;
        if (yield <= 0) return 0;
        return (int) Math.ceil(demandPerSec * producer.secondsPerCycle() / yield);
    }

    /**
     * Combined per-second supply that source steps feed into the given input port
     * of the target node. 0 if that port is not fed by any source step.
     */
    private static float stepSupplyPerSec(final Graph graph, final Map<UUID, List<Edge>> inEdges, final UUID targetId,
        final int targetInputIndex) {
        float supply = 0f;
        for (final Edge edge : inEdges.get(targetId)) {
            if (edge.targetInputIndex != targetInputIndex) continue;
            final Step src = graph.getSteps()
                .get(edge.sourceId);
            if (src == null) continue;
            final List<Port<?>> outs = src.getOutputs();
            if (outs.isEmpty()) continue;
            supply += outs.getFirst()
                .getAmount();
        }
        return supply;
    }

    @Nullable
    private static List<UUID> topologicalSort(final Graph graph, final Map<UUID, List<Edge>> inEdges) {
        final Map<UUID, Integer> inDegree = new HashMap<>();
        for (final Node node : graph.getNodes()
            .values()) {
            inDegree.put(
                node.id,
                inEdges.get(node.id)
                    .size());
        }

        final Deque<UUID> queue = new ArrayDeque<>();
        for (final Map.Entry<UUID, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        final List<UUID> result = new ArrayList<>();
        final Map<UUID, List<Edge>> out = new HashMap<>();
        for (final Node node : graph.getNodes()
            .values()) {
            out.put(node.id, new ArrayList<>());
        }
        for (final Edge edge : graph.getEdges()
            .values()) {
            final List<Edge> list = out.get(edge.sourceId);
            if (list == null) continue;
            list.add(edge);
        }

        while (!queue.isEmpty()) {
            final UUID id = queue.poll();
            result.add(id);
            for (final Edge edge : out.get(id)) {
                if (!graph.getNodes()
                    .containsKey(edge.targetId)) continue;
                final int deg = inDegree.get(edge.targetId) - 1;
                inDegree.put(edge.targetId, deg);
                if (deg == 0) queue.add(edge.targetId);
            }
        }

        if (result.size() != graph.getNodes()
            .size()) {
            return null;
        }
        return result;
    }

    @Nonnull
    private static BalanceResult fallbackBalance(final Graph graph) {
        final Map<UUID, Integer> ops = new HashMap<>();
        for (final Node node : graph.getNodes()
            .values()) {
            ops.put(node.id, 1);
        }
        return buildResult(graph, ops);
    }

    @Nonnull
    private static BalanceResult buildResult(final Graph graph, final Map<UUID, Integer> ops) {
        final Map<UUID, NodeBalance> nodeBalances = new HashMap<>();
        final Map<RecipeProperty<?>, Long> propertyTotals = new HashMap<>();
        int totalOps = 0;
        int totalDuration = 0;

        for (final Node node : graph.getNodes().values()) {
            final int opCount = ops.get(node.id);
            totalOps += opCount;

            final MachineConfig cfg = node.machineConfig;

            final int recipeDuration = node.durationTicks;
            final var eff = cfg.computeEffect(node.properties, recipeDuration);
            final long eutPerOp = eff.energyPerT();
            final int durPerOp = eff.durationTicks();
            final int throughputFactor = eff.throughputFactor();

            final long totalEnergy = eutPerOp * durPerOp * opCount;
            final int totalDurationTicksForNode = durPerOp * opCount;
            totalDuration += totalDurationTicksForNode;

            final Map<Integer, Float> effOuts = new HashMap<>();
            for (int i = 0; i < node.outputs.size(); i++) {
                final int stackSize = node.outputs.get(i).getAmount();
                if (stackSize <= 0) continue;
                final float chance = node.outputs.get(i).getChance();
                final float total = opCount * stackSize * chance * cfg.outputMultiplier(i) * throughputFactor;
                if (total <= 0) continue;
                effOuts.put(i, total);
            }

            final Map<Integer, Float> effIns = new HashMap<>();
            for (int i = 0; i < node.inputs.size(); i++) {
                final int stackSize = node.inputs.get(i).getAmount();
                if (stackSize <= 0) continue;
                final float chance = node.inputs.get(i).getChance();
                final float total = opCount * stackSize * chance * cfg.inputMultiplier(i) * throughputFactor;
                if (total <= 0) continue;
                effIns.put(i, total);
            }

            nodeBalances.put(
                node.id,
                new NodeBalance(opCount, totalDurationTicksForNode, totalEnergy, durPerOp, effOuts, effIns));

            for (final Map.Entry<RecipeProperty<?>, Object> entry : node.properties.entrySet()) {
                final RecipeProperty<?> prop = entry.getKey();
                final Object val = entry.getValue();
                if (val instanceof final Number num) {
                    propertyTotals.merge(prop, num.longValue() * opCount, Long::sum);
                }
            }
        }

        return new BalanceResult(
            nodeBalances,
            propertyTotals,
            totalOps,
            totalDuration);
    }

    public static class NodeBalance {

        public final int operations;
        public final int totalDurationTicks;
        public final long totalEnergy;
        public final int durationPerOp;
        public final Map<Integer, Float> effectiveOutputs;
        public final Map<Integer, Float> effectiveInputs;

        NodeBalance(final int operations, final int totalDurationTicks, final long totalEnergy, final int durationPerOp,
            final Map<Integer, Float> effectiveOutputs, final Map<Integer, Float> effectiveInputs) {
            this.operations = operations;
            this.totalDurationTicks = totalDurationTicks;
            this.totalEnergy = totalEnergy;
            this.durationPerOp = durationPerOp;
            this.effectiveOutputs = effectiveOutputs;
            this.effectiveInputs = effectiveInputs;
        }
    }

    public record BalanceResult(Map<UUID, NodeBalance> nodeBalances, Map<RecipeProperty<?>, Long> propertyTotals,
        int totalOperations, int totalDurationTicks) {}
}
