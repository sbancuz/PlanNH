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

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeProperty;

public class Balancer {

    public static BalanceResult balance(Graph graph) {
        Map<UUID, List<Edge>> outEdges = new HashMap<>();
        Map<UUID, List<Edge>> inEdges = new HashMap<>();
        for (Node node : graph.getNodes()) {
            outEdges.put(node.id, new ArrayList<>());
            inEdges.put(node.id, new ArrayList<>());
        }
        for (Edge edge : graph.getEdges()) {
            outEdges.get(edge.sourceNodeId)
                .add(edge);
            inEdges.get(edge.targetNodeId)
                .add(edge);
        }

        Set<UUID> leafNodes = new HashSet<>();
        for (Node node : graph.getNodes()) {
            if (outEdges.get(node.id)
                .isEmpty()) {
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
        for (Node node : graph.getNodes()) {
            ops.put(node.id, leafNodes.contains(node.id) ? 1 : 0);
        }

        Map<UUID, Integer> throughputFactors = new HashMap<>();
        for (Node node : graph.getNodes()) {
            MachineConfig cfg = node.machineConfig;
            var eff = cfg.computeEffect(node.properties.asMap(), node.durationTicks);
            throughputFactors.put(node.id, eff.throughputFactor());
        }

        for (UUID nodeId : reverseTopo) {
            Node node = graph.nodes.get(nodeId);
            if (node == null) continue;

            int currentOps = ops.get(nodeId);

            Map<Integer, Float> itemsNeededPerPort = new HashMap<>();
            Map<Integer, Float> yieldPerPort = new HashMap<>();
            for (Edge edge : outEdges.get(nodeId)) {
                Node target = graph.nodes.get(edge.targetNodeId);
                if (target == null) continue;

                int targetOps = ops.get(edge.targetNodeId);
                if (targetOps <= 0) continue;

                int targetInputCount = 0;
                float inputChance = 1.f;
                if (edge.targetInputIndex >= 0 && edge.targetInputIndex < target.inputs.size()) {
                    ItemStack stack = target.inputs.get(edge.targetInputIndex)
                        .left();
                    if (stack != null) {
                        targetInputCount = stack.stackSize;
                        inputChance = target.inputs.get(edge.targetInputIndex)
                            .rightFloat();
                    }
                }
                if (targetInputCount <= 0) continue;

                int myOutputCount = 0;
                float outputChance = 1.f;
                if (edge.sourceOutputIndex >= 0 && edge.sourceOutputIndex < node.outputs.size()) {
                    ItemStack stack = node.outputs.get(edge.sourceOutputIndex)
                        .left();
                    if (stack != null) {
                        myOutputCount = stack.stackSize;
                        outputChance = node.outputs.get((edge.sourceOutputIndex))
                            .rightFloat();
                    }
                }
                if (myOutputCount <= 0) continue;

                MachineConfig cfg = node.machineConfig;
                MachineConfig tgtCfg = target.machineConfig;
                int srcThroughput = throughputFactors.get(nodeId);
                int tgtThroughput = throughputFactors.get(edge.targetNodeId);

                float yield = myOutputCount * outputChance
                    * cfg.outputMultiplier(edge.sourceOutputIndex)
                    * srcThroughput;

                float itemsNeeded = targetOps * targetInputCount
                    * inputChance
                    * tgtCfg.inputMultiplier(edge.targetInputIndex)
                    * tgtThroughput;

                if (yield <= 0) continue;

                int port = edge.sourceOutputIndex;
                itemsNeededPerPort.merge(port, itemsNeeded, Float::sum);
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
                ops.put(nodeId, 1);
            }
        }

        return buildResult(graph, ops, throughputFactors);
    }

    private static List<UUID> topologicalSort(Graph graph, Map<UUID, List<Edge>> inEdges) {
        Map<UUID, Integer> inDegree = new HashMap<>();
        for (Node node : graph.getNodes()) {
            inDegree.put(
                node.id,
                inEdges.get(node.id)
                    .size());
        }

        Deque<UUID> queue = new ArrayDeque<>();
        for (Map.Entry<UUID, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<UUID> result = new ArrayList<>();
        Map<UUID, List<Edge>> out = new HashMap<>();
        for (Node node : graph.getNodes()) {
            out.put(node.id, new ArrayList<>());
        }
        for (Edge edge : graph.getEdges()) {
            out.get(edge.sourceNodeId)
                .add(edge);
        }

        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            result.add(id);
            for (Edge edge : out.get(id)) {
                int deg = inDegree.get(edge.targetNodeId) - 1;
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

    private static BalanceResult fallbackBalance(Graph graph) {
        Map<UUID, Integer> ops = new HashMap<>();
        for (Node node : graph.getNodes()) {
            ops.put(node.id, 1);
        }
        Map<UUID, Integer> throughputFactors = new HashMap<>();
        for (Node node : graph.getNodes()) {
            throughputFactors.put(
                node.id,
                node.machineConfig.computeEffect(node.properties.asMap(), node.durationTicks)
                    .throughputFactor());
        }
        return buildResult(graph, ops, throughputFactors);
    }

    private static BalanceResult buildResult(Graph graph, Map<UUID, Integer> ops,
        Map<UUID, Integer> throughputFactors) {
        Map<UUID, NodeBalance> nodeBalances = new HashMap<>();
        Map<RecipeProperty<?>, Long> propertyTotals = new HashMap<>();
        int totalOps = 0;
        int totalDuration = 0;

        for (Node node : graph.getNodes()) {
            int opCount = ops.get(node.id);
            totalOps += opCount;

            MachineConfig cfg = node.machineConfig;

            int recipeDuration = node.durationTicks;
            var eff = cfg.computeEffect(node.properties.asMap(), recipeDuration);
            long eutPerOp = eff.energyPerT();
            int durPerOp = eff.durationTicks();
            int throughputFactor = eff.throughputFactor();

            long totalEnergy = eutPerOp * durPerOp * opCount;
            int totalDurationTicksForNode = durPerOp * opCount;
            totalDuration += totalDurationTicksForNode;

            Map<Integer, Float> effOuts = new HashMap<>();
            for (int i = 0; i < node.outputs.size(); i++) {
                var pair = node.outputs.get(i);
                if (pair == null || pair.left() == null || pair.left().stackSize <= 0) continue;
                float total = opCount * pair.left().stackSize
                    * pair.rightFloat()
                    * cfg.outputMultiplier(i)
                    * throughputFactor;
                if (total > 0) effOuts.put(i, total);
            }

            Map<Integer, Integer> effIns = new HashMap<>();
            for (int i = 0; i < node.inputs.size(); i++) {
                var pair = node.inputs.get(i);
                if (pair == null || pair.left() == null || pair.left().stackSize <= 0) continue;
                int total = Math.round(
                    opCount * pair.left().stackSize * pair.rightFloat() * cfg.inputMultiplier(i) * throughputFactor);
                if (total > 0) effIns.put(i, total);
            }

            nodeBalances.put(
                node.id,
                new NodeBalance(opCount, totalDurationTicksForNode, totalEnergy, durPerOp, effOuts, effIns));

            for (Map.Entry<RecipeProperty<?>, Object> entry : node.properties.entrySet()) {
                RecipeProperty<?> prop = entry.getKey();
                Object val = entry.getValue();
                if (prop == RecipePropertyAPI.EU_PER_TICK) continue;
                if (val instanceof Number num) {
                    propertyTotals.merge(prop, num.longValue() * opCount, Long::sum);
                }
            }
        }

        Set<String> fulfilledInputs = new HashSet<>();
        Set<String> consumedOutputs = new HashSet<>();
        for (Edge edge : graph.getEdges()) {
            fulfilledInputs.add(edge.targetNodeId + ":" + edge.targetInputIndex);
            consumedOutputs.add(edge.sourceNodeId + ":" + edge.sourceOutputIndex);
        }

        List<Summary.SummaryLine> netInputs = new ArrayList<>();
        List<Summary.SummaryLine> netOutputs = new ArrayList<>();
        List<Summary.FluidSummaryLine> netFluidInputs = new ArrayList<>();
        List<Summary.FluidSummaryLine> netFluidOutputs = new ArrayList<>();

        for (Node node : graph.getNodes()) {
            NodeBalance nb = nodeBalances.get(node.id);
            MachineConfig cfg = node.machineConfig;

            int nodeOps = ops.get(node.id);
            int throughputFactor = throughputFactors.getOrDefault(node.id, 1);

            for (int i = 0; i < node.inputs.size(); i++) {
                ItemStack stack = node.inputs.get(i)
                    .left();
                if (stack == null || stack.stackSize <= 0) continue;
                if (fulfilledInputs.contains(node.id + ":" + i)) continue;
                Integer totalCount = nb.effectiveInputs.get(i);
                if (totalCount != null && totalCount > 0) {
                    Summary.mergeInto(netInputs, stack, totalCount);
                }
            }
            for (int i = 0; i < node.outputs.size(); i++) {
                ItemStack stack = node.outputs.get(i)
                    .left();
                if (stack == null || stack.stackSize <= 0) continue;
                if (consumedOutputs.contains(node.id + ":" + i)) continue;
                Float totalCount = nb.effectiveOutputs.get(i);
                if (totalCount != null && totalCount > 0) {
                    Summary.mergeInto(netOutputs, stack, (int) Math.ceil(totalCount));
                }
            }

            for (int i = 0; i < node.fluidInputs.size(); i++) {
                FluidStack fs = node.fluidInputs.get(i)
                    .left();
                if (fs == null || fs.amount <= 0) continue;
                int combinedIdx = node.inputs.size() + i;
                if (fulfilledInputs.contains(node.id + ":" + combinedIdx)) continue;
                int total = Math.round(
                    nodeOps * fs.amount
                        * node.fluidInputs.get(i)
                            .rightFloat()
                        * throughputFactor);
                if (total > 0) {
                    Summary.mergeFluidInto(netFluidInputs, fs, total);
                }
            }
            for (int i = 0; i < node.fluidOutputs.size(); i++) {
                FluidStack fs = node.fluidOutputs.get(i)
                    .left();
                if (fs == null || fs.amount <= 0) continue;
                int combinedIdx = node.outputs.size() + i;
                if (consumedOutputs.contains(node.id + ":" + combinedIdx)) continue;
                int total = Math.round(
                    nodeOps * fs.amount
                        * node.fluidOutputs.get(i)
                            .rightFloat()
                        * throughputFactor);
                if (total > 0) {
                    Summary.mergeFluidInto(netFluidOutputs, fs, total);
                }
            }
        }

        return new BalanceResult(
            nodeBalances,
            netInputs,
            netOutputs,
            netFluidInputs,
            netFluidOutputs,
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
        public final Map<Integer, Integer> effectiveInputs;

        NodeBalance(int operations, int totalDurationTicks, long totalEnergy, int durationPerOp,
            Map<Integer, Float> effectiveOutputs, Map<Integer, Integer> effectiveInputs) {
            this.operations = operations;
            this.totalDurationTicks = totalDurationTicks;
            this.totalEnergy = totalEnergy;
            this.durationPerOp = durationPerOp;
            this.effectiveOutputs = effectiveOutputs;
            this.effectiveInputs = effectiveInputs;
        }
    }

    public record BalanceResult(Map<UUID, NodeBalance> nodeBalances, List<Summary.SummaryLine> netInputs,
        List<Summary.SummaryLine> netOutputs, List<Summary.FluidSummaryLine> netFluidInputs,
        List<Summary.FluidSummaryLine> netFluidOutputs, Map<RecipeProperty<?>, Long> propertyTotals,
        int totalOperations, int totalDurationTicks) {}
}
