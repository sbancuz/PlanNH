package com.sbancuz.plannh.data.flowchart;

import static com.sbancuz.plannh.data.provider.GTProvider.EU_PER_TICK;

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

    @Nonnull
    private static BalanceResult balanceNone(final Graph graph) {
        final Map<UUID, Integer> ops = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            ops.put(node.id, 1);
        }
        return buildResult(graph, ops);
    }

    @Nonnull
    private static BalanceResult balanceForward(final Graph graph) {
        final Map<UUID, List<Edge>> outEdges = new HashMap<>();
        final Map<UUID, List<Edge>> inEdges = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            outEdges.put(node.id, new ArrayList<>());
            inEdges.put(node.id, new ArrayList<>());
        }
        for (final Edge edge : graph.getEdges()) {
            outEdges.get(edge.sourceNodeId)
                .add(edge);
            inEdges.get(edge.targetNodeId)
                .add(edge);
        }

        final List<UUID> topoOrder = topologicalSort(graph, inEdges);
        if (topoOrder == null) {
            return balanceNone(graph);
        }

        final Map<UUID, Integer> ops = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            ops.put(
                node.id,
                inEdges.get(node.id)
                    .isEmpty() ? 1 : 0);
        }

        final Map<UUID, Integer> throughputFactors = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            final MachineConfig cfg = node.machineConfig;
            final var eff = cfg.computeEffect(node.properties, node.durationTicks);
            throughputFactors.put(node.id, eff.throughputFactor());
        }

        for (final UUID nodeId : topoOrder) {
            final Node node = graph.nodes.get(nodeId);
            if (node == null) continue;

            final int currentOps = ops.get(nodeId);
            if (currentOps <= 0) continue;

            for (final Edge edge : outEdges.get(nodeId)) {
                final Node target = graph.nodes.get(edge.targetNodeId);
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
                final int tgtThroughput = throughputFactors.get(edge.targetNodeId);

                final float yield = currentOps * myOutputCount
                    * outputChance
                    * cfg.outputMultiplier(edge.sourceOutputIndex)
                    * srcThroughput;

                final float demandPerOp = targetInputCount * inputChance
                    * tgtCfg.inputMultiplier(edge.targetInputIndex)
                    * tgtThroughput;

                if (demandPerOp <= 0) continue;

                final int needed = (int) Math.ceil(yield / demandPerOp);
                final int existing = ops.get(edge.targetNodeId);
                if (needed > existing) {
                    ops.put(edge.targetNodeId, needed);
                }
            }
        }

        for (final Node node : graph.getNodes()) {
            final int v = ops.get(node.id);
            if (v <= 0) ops.put(node.id, 1);
        }

        return buildResult(graph, ops);
    }

    @Nonnull
    private static BalanceResult balanceBackward(final Graph graph) {
        final Map<UUID, List<Edge>> outEdges = new HashMap<>();
        final Map<UUID, List<Edge>> inEdges = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            outEdges.put(node.id, new ArrayList<>());
            inEdges.put(node.id, new ArrayList<>());
        }
        for (final Edge edge : graph.getEdges()) {
            outEdges.get(edge.sourceNodeId)
                .add(edge);
            inEdges.get(edge.targetNodeId)
                .add(edge);
        }

        final Set<UUID> leafNodes = new HashSet<>();
        for (final Node node : graph.getNodes()) {
            if (outEdges.get(node.id)
                .isEmpty()) {
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
        for (final Node node : graph.getNodes()) {
            ops.put(node.id, leafNodes.contains(node.id) ? 1 : 0);
        }

        final Map<UUID, Integer> throughputFactors = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            final MachineConfig cfg = node.machineConfig;
            final var eff = cfg.computeEffect(node.properties, node.durationTicks);
            throughputFactors.put(node.id, eff.throughputFactor());
        }

        for (final UUID nodeId : reverseTopo) {
            final Node node = graph.nodes.get(nodeId);
            if (node == null) continue;

            final int currentOps = ops.get(nodeId);

            final Map<Integer, Float> itemsNeededPerPort = new HashMap<>();
            final Map<Integer, Float> yieldPerPort = new HashMap<>();
            for (final Edge edge : outEdges.get(nodeId)) {
                final Node target = graph.nodes.get(edge.targetNodeId);
                if (target == null) continue;

                final int targetOps = ops.get(edge.targetNodeId);
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
                final int tgtThroughput = throughputFactors.get(edge.targetNodeId);

                final float yield = myOutputCount * outputChance
                    * cfg.outputMultiplier(edge.sourceOutputIndex)
                    * srcThroughput;

                final float itemsNeeded = targetOps * targetInputCount
                    * inputChance
                    * tgtCfg.inputMultiplier(edge.targetInputIndex)
                    * tgtThroughput;

                if (yield <= 0) continue;

                final int port = edge.sourceOutputIndex;
                itemsNeededPerPort.merge(port, itemsNeeded, Float::sum);
                yieldPerPort.put(port, yield);
            }

            int maxDemand = 0;
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

    @Nullable
    private static List<UUID> topologicalSort(final Graph graph, final Map<UUID, List<Edge>> inEdges) {
        final Map<UUID, Integer> inDegree = new HashMap<>();
        for (final Node node : graph.getNodes()) {
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
        for (final Node node : graph.getNodes()) {
            out.put(node.id, new ArrayList<>());
        }
        for (final Edge edge : graph.getEdges()) {
            out.get(edge.sourceNodeId)
                .add(edge);
        }

        while (!queue.isEmpty()) {
            final UUID id = queue.poll();
            result.add(id);
            for (final Edge edge : out.get(id)) {
                final int deg = inDegree.get(edge.targetNodeId) - 1;
                inDegree.put(edge.targetNodeId, deg);
                if (deg == 0) queue.add(edge.targetNodeId);
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
        for (final Node node : graph.getNodes()) {
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

        for (final Node node : graph.getNodes()) {
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
                if (prop == EU_PER_TICK) continue;
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
