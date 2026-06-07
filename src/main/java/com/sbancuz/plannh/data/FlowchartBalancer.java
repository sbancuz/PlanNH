package com.sbancuz.plannh.data;

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

import com.sbancuz.plannh.api.RecipePropertyAPI;
import net.minecraft.item.ItemStack;

public class FlowchartBalancer {

    public static BalanceResult balance(FlowchartGraph graph) {
        Map<UUID, List<FlowchartEdge>> outEdges = new HashMap<>();
        Map<UUID, List<FlowchartEdge>> inEdges = new HashMap<>();
        for (FlowchartNode node : graph.getNodes()) {
            outEdges.put(node.id, new ArrayList<>());
            inEdges.put(node.id, new ArrayList<>());
        }
        for (FlowchartEdge edge : graph.getEdges()) {
            outEdges.get(edge.sourceNodeId).add(edge);
            inEdges.get(edge.targetNodeId).add(edge);
        }

        Set<UUID> leafNodes = new HashSet<>();
        for (FlowchartNode node : graph.getNodes()) {
            if (outEdges.get(node.id).isEmpty()) {
                leafNodes.add(node.id);
            }
        }

        List<UUID> topoOrder = topologicalSort(graph, inEdges);
        if (topoOrder == null) {
            return fallbackBalance(graph);
        }

        List<UUID> reverseTopo = new ArrayList<>(topoOrder);
        Collections.reverse(reverseTopo);

        Map<UUID, Integer> ops = new HashMap<>();
        for (FlowchartNode node : graph.getNodes()) {
            ops.put(node.id, leafNodes.contains(node.id) ? 1 : 0);
        }

        for (UUID nodeId : reverseTopo) {
            FlowchartNode node = graph.nodes.get(nodeId);
            if (node == null) continue;

            int currentOps = ops.get(nodeId);

            // Group demand per output port — same output feeding multiple consumers must sum item counts
            Map<Integer, Float> itemsNeededPerPort = new HashMap<>();
            Map<Integer, Float> yieldPerPort = new HashMap<>();
            for (FlowchartEdge edge : outEdges.get(nodeId)) {
                FlowchartNode target = graph.nodes.get(edge.targetNodeId);
                if (target == null) continue;

                int targetOps = ops.get(edge.targetNodeId);
                if (targetOps <= 0) continue;

                int targetInputCount = 0;
                if (edge.targetInputIndex >= 0 && edge.targetInputIndex < target.inputs.size()) {
                    ItemStack stack = target.inputs.get(edge.targetInputIndex);
                    if (stack != null) targetInputCount = stack.stackSize;
                }
                if (targetInputCount <= 0) continue;

                int myOutputCount = 0;
                if (edge.sourceOutputIndex >= 0 && edge.sourceOutputIndex < node.outputs.size()) {
                    ItemStack stack = node.outputs.get(edge.sourceOutputIndex);
                    if (stack != null) myOutputCount = stack.stackSize;
                }
                if (myOutputCount <= 0) continue;

                float[] chances = node.properties.get(RecipePropertyAPI.OUTPUT_CHANCES);
                float chance = 1.0f;
                if (chances != null && edge.sourceOutputIndex < chances.length) {
                    chance = chances[edge.sourceOutputIndex];
                }

                float yield = myOutputCount * chance;
                if (yield <= 0) continue;

                int port = edge.sourceOutputIndex;
                itemsNeededPerPort.merge(port, (float) (targetOps * targetInputCount), Float::sum);
                yieldPerPort.put(port, yield);
            }

            int maxDemand = 0;
            for (Map.Entry<Integer, Float> entry : itemsNeededPerPort.entrySet()) {
                int port = entry.getKey();
                float itemsNeeded = entry.getValue();
                float yield = yieldPerPort.get(port);
                int needed = (int) Math.ceil(itemsNeeded / yield);
                if (needed > maxDemand) maxDemand = needed;
            }

            if (maxDemand > currentOps) {
                ops.put(nodeId, maxDemand);
            } else if (currentOps == 0) {
                ops.put(nodeId, Math.max(1, maxDemand));
            }
        }

        return buildResult(graph, ops, outEdges);
    }

    private static List<UUID> topologicalSort(FlowchartGraph graph, Map<UUID, List<FlowchartEdge>> inEdges) {
        Map<UUID, Integer> inDegree = new HashMap<>();
        for (FlowchartNode node : graph.getNodes()) {
            inDegree.put(node.id, inEdges.get(node.id).size());
        }

        Deque<UUID> queue = new ArrayDeque<>();
        for (Map.Entry<UUID, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<UUID> result = new ArrayList<>();
        Map<UUID, List<FlowchartEdge>> out = new HashMap<>();
        for (FlowchartNode node : graph.getNodes()) {
            out.put(node.id, new ArrayList<>());
        }
        for (FlowchartEdge edge : graph.getEdges()) {
            out.get(edge.sourceNodeId).add(edge);
        }

        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            result.add(id);
            for (FlowchartEdge edge : out.get(id)) {
                int deg = inDegree.get(edge.targetNodeId) - 1;
                inDegree.put(edge.targetNodeId, deg);
                if (deg == 0) queue.add(edge.targetNodeId);
            }
        }

        if (result.size() != graph.getNodes().size()) {
            return null;
        }
        return result;
    }

    private static BalanceResult fallbackBalance(FlowchartGraph graph) {
        Map<UUID, Integer> ops = new HashMap<>();
        for (FlowchartNode node : graph.getNodes()) {
            ops.put(node.id, 1);
        }
        Map<UUID, List<FlowchartEdge>> outEdges = new HashMap<>();
        for (FlowchartNode node : graph.getNodes()) {
            outEdges.put(node.id, new ArrayList<>());
        }
        for (FlowchartEdge edge : graph.getEdges()) {
            outEdges.get(edge.sourceNodeId).add(edge);
        }
        return buildResult(graph, ops, outEdges);
    }

    private static BalanceResult buildResult(FlowchartGraph graph, Map<UUID, Integer> ops,
        Map<UUID, List<FlowchartEdge>> outEdges) {
        Map<UUID, NodeBalance> nodeBalances = new HashMap<>();
        Map<RecipeProperty<?>, Long> propertyTotals = new HashMap<>();
        int totalOps = 0;
        int totalDuration = 0;

        for (FlowchartNode node : graph.getNodes()) {
            int opCount = ops.get(node.id);
            totalOps += opCount;

            long energy = extractEnergy(node, opCount);
            int duration = node.durationTicks * opCount;
            totalDuration += duration;

            Map<Integer, Float> effOuts = new HashMap<>();
            for (int i = 0; i < node.outputs.size(); i++) {
                ItemStack stack = node.outputs.get(i);
                if (stack == null || stack.stackSize <= 0) continue;
                float[] chances = node.properties.get(RecipePropertyAPI.OUTPUT_CHANCES);
                float chance = 1.0f;
                if (chances != null && i < chances.length) {
                    chance = chances[i];
                }
                effOuts.put(i, opCount * stack.stackSize * chance);
            }

            Map<Integer, Integer> effIns = new HashMap<>();
            for (int i = 0; i < node.inputs.size(); i++) {
                ItemStack stack = node.inputs.get(i);
                if (stack == null || stack.stackSize <= 0) continue;
                effIns.put(i, opCount * stack.stackSize);
            }

            nodeBalances.put(node.id, new NodeBalance(opCount, duration, energy, effOuts, effIns));

            for (Map.Entry<RecipeProperty<?>, Object> entry : node.properties.entrySet()) {
                RecipeProperty<?> prop = entry.getKey();
                Object val = entry.getValue();
                if (prop == RecipePropertyAPI.OUTPUT_CHANCES) continue;
                if (prop == RecipePropertyAPI.EU_PER_TICK) continue;
                if (val instanceof Number num) {
                    propertyTotals.merge(prop, num.longValue() * opCount, Long::sum);
                }
            }
        }

        Set<String> fulfilledInputs = new HashSet<>();
        Set<String> consumedOutputs = new HashSet<>();
        for (FlowchartEdge edge : graph.getEdges()) {
            fulfilledInputs.add(edge.targetNodeId + ":" + edge.targetInputIndex);
            consumedOutputs.add(edge.sourceNodeId + ":" + edge.sourceOutputIndex);
        }

        List<FlowchartSummary.SummaryLine> netInputs = new ArrayList<>();
        List<FlowchartSummary.SummaryLine> netOutputs = new ArrayList<>();

        for (FlowchartNode node : graph.getNodes()) {
            NodeBalance nb = nodeBalances.get(node.id);
            for (int i = 0; i < node.inputs.size(); i++) {
                ItemStack stack = node.inputs.get(i);
                if (stack == null || stack.stackSize <= 0) continue;
                if (fulfilledInputs.contains(node.id + ":" + i)) continue;
                int totalCount = nb.effectiveInputs.get(i);
                FlowchartSummary.mergeInto(netInputs, stack, totalCount);
            }
            for (int i = 0; i < node.outputs.size(); i++) {
                ItemStack stack = node.outputs.get(i);
                if (stack == null || stack.stackSize <= 0) continue;
                if (consumedOutputs.contains(node.id + ":" + i)) continue;
                float totalCount = nb.effectiveOutputs.get(i);
                if (totalCount > 0) {
                    int intCount = (int) Math.ceil(totalCount);
                    FlowchartSummary.mergeInto(netOutputs, stack, intCount);
                }
            }
        }

        return new BalanceResult(
            nodeBalances, netInputs, netOutputs,
            propertyTotals, totalOps, totalDuration);
    }

    private static long extractEnergy(FlowchartNode node, int ops) {
        Long totalEu = node.properties.get(RecipePropertyAPI.TOTAL_EU);
        if (totalEu != null && totalEu > 0) return totalEu * ops;
        for (Map.Entry<RecipeProperty<?>, Object> entry : node.properties.entrySet()) {
            RecipeProperty<?> prop = entry.getKey();
            Object val = entry.getValue();
            if (prop == RecipePropertyAPI.TOTAL_EU) continue;
            if (prop == RecipePropertyAPI.OUTPUT_CHANCES) continue;
            if (prop == RecipePropertyAPI.EU_PER_TICK) continue;
            if (prop == RecipePropertyAPI.DURATION_TICKS) continue;
            if (val instanceof Integer i && i > 0) return (long) i * ops;
            if (val instanceof Long l && l > 0) return l * ops;
        }
        return 0;
    }

    public static class NodeBalance {
        public final int operations;
        public final int totalDurationTicks;
        public final long totalEnergy;
        public final Map<Integer, Float> effectiveOutputs;
        public final Map<Integer, Integer> effectiveInputs;

        NodeBalance(int operations, int totalDurationTicks, long totalEnergy,
            Map<Integer, Float> effectiveOutputs, Map<Integer, Integer> effectiveInputs) {
            this.operations = operations;
            this.totalDurationTicks = totalDurationTicks;
            this.totalEnergy = totalEnergy;
            this.effectiveOutputs = effectiveOutputs;
            this.effectiveInputs = effectiveInputs;
        }
    }

    public record BalanceResult(
        Map<UUID, NodeBalance> nodeBalances,
        List<FlowchartSummary.SummaryLine> netInputs,
        List<FlowchartSummary.SummaryLine> netOutputs,
        Map<RecipeProperty<?>, Long> propertyTotals,
        int totalOperations,
        int totalDurationTicks
    ) {}
}
