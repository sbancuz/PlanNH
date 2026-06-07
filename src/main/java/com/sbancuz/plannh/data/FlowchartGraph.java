package com.sbancuz.plannh.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;

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

    public FlowchartSummary calculateSummary() {
        Set<String> fulfilledInputs = new HashSet<>();
        Set<String> consumedOutputs = new HashSet<>();

        for (FlowchartEdge edge : edges.values()) {
            fulfilledInputs.add(edge.targetNodeId + ":" + edge.targetInputIndex);
            consumedOutputs.add(edge.sourceNodeId + ":" + edge.sourceOutputIndex);
        }

        ArrayList<FlowchartSummary.SummaryLine> netIns = new ArrayList<>();
        ArrayList<FlowchartSummary.SummaryLine> netOuts = new ArrayList<>();

        long eu = 0;
        for (FlowchartNode node : nodes.values()) {
            eu += node.totalEu;
            for (int i = 0; i < node.inputs.size(); i++) {
                ItemStack stack = node.inputs.get(i);
                if (stack != null && stack.stackSize > 0 && !fulfilledInputs.contains(node.id + ":" + i)) {
                    FlowchartSummary.mergeInto(netIns, stack, stack.stackSize);
                }
            }
            for (int i = 0; i < node.outputs.size(); i++) {
                ItemStack stack = node.outputs.get(i);
                if (stack != null && stack.stackSize > 0 && !consumedOutputs.contains(node.id + ":" + i)) {
                    FlowchartSummary.mergeInto(netOuts, stack, stack.stackSize);
                }
            }
        }

        return new FlowchartSummary(netIns, netOuts, eu);
    }

    public Collection<FlowchartNode> getNodes() {
        return nodes.values();
    }

    public Collection<FlowchartEdge> getEdges() {
        return edges.values();
    }
}
