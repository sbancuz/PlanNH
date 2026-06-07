package com.sbancuz.plannh.data;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlowchartGraph {

    public final Map<UUID, FlowchartNode> nodes = new HashMap<>();
    public final Map<UUID, FlowchartEdge> edges = new HashMap<>();

    public void addNode(FlowchartNode node) {
        nodes.put(node.id, node);
    }

    public void removeNode(UUID id) {
        nodes.remove(id);
        edges.values()
            .removeIf(e -> e.sourceNodeId.equals(id) || e.targetNodeId.equals(id));
    }

    public void addEdge(FlowchartEdge edge) {
        edges.put(edge.id, edge);
    }

    public void removeEdge(UUID id) {
        edges.remove(id);
    }

    // public FlowchartSummary calculateSummary() {
    // Set<String> fulfilledInputs = new HashSet<>();
    // Set<String> consumedOutputs = new HashSet<>();
    //
    // for (FlowchartEdge edge : edges.values()) {
    // fulfilledInputs.add(edge.targetNodeId + ":" + edge.targetInputIndex);
    // consumedOutputs.add(edge.sourceNodeId + ":" + edge.sourceOutputIndex);
    // }
    //
    // ArrayList<FlowchartSummary.SummaryLine> netIns = new ArrayList<>();
    // ArrayList<FlowchartSummary.SummaryLine> netOuts = new ArrayList<>();
    //
    // Map<RecipeProperty<?>, Long> totals = new HashMap<>();
    // for (FlowchartNode node : nodes.values()) {
    // for (Map.Entry<RecipeProperty<?>, Object> entry : node.properties.entrySet()) {
    // RecipeProperty<?> prop = entry.getKey();
    // Object val = entry.getValue();
    // if (val instanceof Number num) {
    // totals.merge(prop, num.longValue(), Long::sum);
    // }
    // }
    // for (int i = 0; i < node.inputs.size(); i++) {
    // ItemStack stack = node.inputs.get(i);
    // if (stack != null && stack.stackSize > 0 && !fulfilledInputs.contains(node.id + ":" + i)) {
    // FlowchartSummary.mergeInto(netIns, stack, stack.stackSize);
    // }
    // }
    // for (int i = 0; i < node.outputs.size(); i++) {
    // ItemStack stack = node.outputs.get(i);
    // if (stack != null && stack.stackSize > 0 && !consumedOutputs.contains(node.id + ":" + i)) {
    // FlowchartSummary.mergeInto(netOuts, stack, stack.stackSize);
    // }
    // }
    // }
    //
    // return new FlowchartSummary(netIns, netOuts, totals);
    // }
    //
    public FlowchartBalancer.BalanceResult balance() {
        return FlowchartBalancer.balance(this);
    }

    public Collection<FlowchartNode> getNodes() {
        return nodes.values();
    }

    public Collection<FlowchartEdge> getEdges() {
        return edges.values();
    }
}
