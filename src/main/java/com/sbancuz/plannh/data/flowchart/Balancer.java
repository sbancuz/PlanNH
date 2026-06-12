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

public final class Balancer {

    public enum BalanceMode {
        NONE,
        FORWARD,
        BACKWARD
    }

    public static BalanceResult balance(final Graph graph, final BalanceMode mode) {
        return switch (mode) {
            case NONE -> balanceNone(graph);
            case FORWARD -> balanceForward(graph);
            case BACKWARD -> balanceBackward(graph);
        };
    }

    private static BalanceResult balanceNone(final Graph graph) {
        final Map<UUID, Integer> ops = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            ops.put(node.id, 1);
        }
        final Map<UUID, Integer> throughputFactors = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            throughputFactors.put(
                node.id,
                node.machineConfig.computeEffect(node.properties.asMap(), node.durationTicks)
                    .throughputFactor());
        }
        return buildResult(graph, ops, throughputFactors);
    }

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
            ops.put(node.id, inEdges.get(node.id)
                .isEmpty() ? 1 : 0);
        }

        final Map<UUID, Integer> throughputFactors = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            final MachineConfig cfg = node.machineConfig;
            final var eff = cfg.computeEffect(node.properties.asMap(), node.durationTicks);
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

                int myOutputCount = 0;
                float outputChance = 1.f;
                if (edge.sourceOutputIndex >= 0 && edge.sourceOutputIndex < node.outputs.size()) {
                    final ItemStack stack = node.outputs.get(edge.sourceOutputIndex)
                        .left();
                    if (stack != null) {
                        myOutputCount = stack.stackSize;
                        outputChance = node.outputs.get(edge.sourceOutputIndex)
                            .rightFloat();
                    }
                }
                if (myOutputCount <= 0) continue;

                int targetInputCount = 0;
                float inputChance = 1.f;
                if (edge.targetInputIndex >= 0 && edge.targetInputIndex < target.inputs.size()) {
                    final ItemStack stack = target.inputs.get(edge.targetInputIndex)
                        .left();
                    if (stack != null) {
                        targetInputCount = stack.stackSize;
                        inputChance = target.inputs.get(edge.targetInputIndex)
                            .rightFloat();
                    }
                }
                if (targetInputCount <= 0) continue;

                final MachineConfig cfg = node.machineConfig;
                final MachineConfig tgtCfg = target.machineConfig;
                final int srcThroughput = throughputFactors.get(nodeId);
                final int tgtThroughput = throughputFactors.get(edge.targetNodeId);

                final float yield = currentOps * myOutputCount * outputChance
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

        return buildResult(graph, ops, throughputFactors);
    }

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
            final var eff = cfg.computeEffect(node.properties.asMap(), node.durationTicks);
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

                int targetInputCount = 0;
                float inputChance = 1.f;
                if (edge.targetInputIndex >= 0 && edge.targetInputIndex < target.inputs.size()) {
                    final ItemStack stack = target.inputs.get(edge.targetInputIndex)
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
                    final ItemStack stack = node.outputs.get(edge.sourceOutputIndex)
                        .left();
                    if (stack != null) {
                        myOutputCount = stack.stackSize;
                        outputChance = node.outputs.get((edge.sourceOutputIndex))
                            .rightFloat();
                    }
                }
                if (myOutputCount <= 0) continue;

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

        return buildResult(graph, ops, throughputFactors);
    }

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

    private static BalanceResult fallbackBalance(final Graph graph) {
        final Map<UUID, Integer> ops = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            ops.put(node.id, 1);
        }
        final Map<UUID, Integer> throughputFactors = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            throughputFactors.put(
                node.id,
                node.machineConfig.computeEffect(node.properties.asMap(), node.durationTicks)
                    .throughputFactor());
        }
        return buildResult(graph, ops, throughputFactors);
    }

    private static BalanceResult buildResult(final Graph graph, final Map<UUID, Integer> ops,
                                             final Map<UUID, Integer> throughputFactors) {
        final Map<UUID, NodeBalance> nodeBalances = new HashMap<>();
        final Map<RecipeProperty<?>, Long> propertyTotals = new HashMap<>();
        int totalOps = 0;
        int totalDuration = 0;

        for (final Node node : graph.getNodes()) {
            final int opCount = ops.get(node.id);
            totalOps += opCount;

            final MachineConfig cfg = node.machineConfig;

            final int recipeDuration = node.durationTicks;
            final var eff = cfg.computeEffect(node.properties.asMap(), recipeDuration);
            final long eutPerOp = eff.energyPerT();
            final int durPerOp = eff.durationTicks();
            final int throughputFactor = eff.throughputFactor();

            final long totalEnergy = eutPerOp * durPerOp * opCount;
            final int totalDurationTicksForNode = durPerOp * opCount;
            totalDuration += totalDurationTicksForNode;

            final Map<Integer, Float> effOuts = new HashMap<>();
            for (int i = 0; i < node.outputs.size(); i++) {
                final var pair = node.outputs.get(i);
                if (pair == null || pair.left() == null || pair.left().stackSize <= 0) continue;
                final float total = opCount * pair.left().stackSize
                    * pair.rightFloat()
                    * cfg.outputMultiplier(i)
                    * throughputFactor;
                if (total > 0) effOuts.put(i, total);
            }

            final Map<Integer, Integer> effIns = new HashMap<>();
            for (int i = 0; i < node.inputs.size(); i++) {
                final var pair = node.inputs.get(i);
                if (pair == null || pair.left() == null || pair.left().stackSize <= 0) continue;
                final int total = Math.round(
                    opCount * pair.left().stackSize * pair.rightFloat() * cfg.inputMultiplier(i) * throughputFactor);
                if (total > 0) effIns.put(i, total);
            }

            nodeBalances.put(
                node.id,
                new NodeBalance(opCount, totalDurationTicksForNode, totalEnergy, durPerOp, effOuts, effIns));

            for (final Map.Entry<RecipeProperty<?>, Object> entry : node.properties.entrySet()) {
                final RecipeProperty<?> prop = entry.getKey();
                final Object val = entry.getValue();
                if (prop == RecipePropertyAPI.EU_PER_TICK) continue;
                if (val instanceof final Number num) {
                    propertyTotals.merge(prop, num.longValue() * opCount, Long::sum);
                }
            }
        }

        final Set<String> fulfilledInputs = new HashSet<>();
        final Set<String> consumedOutputs = new HashSet<>();
        for (final Edge edge : graph.getEdges()) {
            fulfilledInputs.add(edge.targetNodeId + ":" + edge.targetInputIndex);
            consumedOutputs.add(edge.sourceNodeId + ":" + edge.sourceOutputIndex);
        }

        final List<Summary.SummaryLine> netInputs = new ArrayList<>();
        final List<Summary.SummaryLine> netOutputs = new ArrayList<>();
        final List<Summary.FluidSummaryLine> netFluidInputs = new ArrayList<>();
        final List<Summary.FluidSummaryLine> netFluidOutputs = new ArrayList<>();

        for (final Node node : graph.getNodes()) {
            final NodeBalance nb = nodeBalances.get(node.id);
            final MachineConfig cfg = node.machineConfig;

            final int nodeOps = ops.get(node.id);
            final int throughputFactor = throughputFactors.getOrDefault(node.id, 1);

            for (int i = 0; i < node.inputs.size(); i++) {
                final ItemStack stack = node.inputs.get(i)
                    .left();
                if (stack == null || stack.stackSize <= 0) continue;
                if (fulfilledInputs.contains(node.id + ":" + i)) continue;
                final Integer totalCount = nb.effectiveInputs.get(i);
                if (totalCount != null && totalCount > 0) {
                    Summary.mergeInto(netInputs, stack, totalCount);
                }
            }
            for (int i = 0; i < node.outputs.size(); i++) {
                final ItemStack stack = node.outputs.get(i)
                    .left();
                if (stack == null || stack.stackSize <= 0) continue;
                if (consumedOutputs.contains(node.id + ":" + i)) continue;
                final Float totalCount = nb.effectiveOutputs.get(i);
                if (totalCount != null && totalCount > 0) {
                    Summary.mergeInto(netOutputs, stack, (int) Math.ceil(totalCount));
                }
            }

            for (int i = 0; i < node.fluidInputs.size(); i++) {
                final FluidStack fs = node.fluidInputs.get(i)
                    .left();
                if (fs == null || fs.amount <= 0) continue;
                final int combinedIdx = node.inputs.size() + i;
                if (fulfilledInputs.contains(node.id + ":" + combinedIdx)) continue;
                final int total = Math.round(
                    nodeOps * fs.amount
                        * node.fluidInputs.get(i)
                            .rightFloat()
                        * throughputFactor);
                if (total > 0) {
                    Summary.mergeFluidInto(netFluidInputs, fs, total);
                }
            }
            for (int i = 0; i < node.fluidOutputs.size(); i++) {
                final FluidStack fs = node.fluidOutputs.get(i)
                    .left();
                if (fs == null || fs.amount <= 0) continue;
                final int combinedIdx = node.outputs.size() + i;
                if (consumedOutputs.contains(node.id + ":" + combinedIdx)) continue;
                final int total = Math.round(
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

        NodeBalance(final int operations, final int totalDurationTicks, final long totalEnergy, final int durationPerOp,
            final Map<Integer, Float> effectiveOutputs, final Map<Integer, Integer> effectiveInputs) {
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
