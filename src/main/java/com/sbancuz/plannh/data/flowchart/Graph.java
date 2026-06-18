package com.sbancuz.plannh.data.flowchart;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

public class Graph {

    public final Map<UUID, Node> nodes = new HashMap<>();
    public final Map<UUID, Edge> edges = new HashMap<>();
    public final Map<UUID, Note> notes = new HashMap<>();
    public final Map<UUID, Group> groups = new HashMap<>();

    @Getter
    @Setter
    private Balancer.BalanceMode balanceMode = Balancer.BalanceMode.BACKWARD;

    public void removeNode(final UUID id) {
        nodes.remove(id);
        edges.values()
            .removeIf(e -> e.sourceNodeId.equals(id) || e.targetNodeId.equals(id));
    }

    public Balancer.BalanceResult balance() {
        return Balancer.balance(this, balanceMode);
    }

    public Collection<Node> getNodes() {
        return nodes.values();
    }

    public void addNode(final Node node) {
        nodes.put(node.id, node);
    }

    public Collection<Edge> getEdges() {
        return edges.values();
    }

    public void addEdge(final Edge edge) {
        edges.put(edge.id, edge);
    }

    public void removeEdge(final UUID id) {
        edges.remove(id);
    }

    public void addGroup(final Group group) {
        groups.put(group.id, group);
    }

    public void removeGroup(final UUID id) {
        groups.remove(id);
    }

    public Collection<Group> getGroups() {
        return groups.values();
    }
}
