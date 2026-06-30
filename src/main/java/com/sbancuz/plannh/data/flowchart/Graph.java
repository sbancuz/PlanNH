package com.sbancuz.plannh.data.flowchart;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

public class Graph {

    // TODO make these use getters
    public final Map<UUID, Node> nodes = new HashMap<>();
    public final Map<UUID, Edge> edges = new HashMap<>();
    public final Map<UUID, Note> notes = new HashMap<>();
    public final Map<UUID, Group> groups = new HashMap<>();

    @Getter
    @Setter
    private float zoom = 1f;
    @Getter
    @Setter
    private float panX;
    @Getter
    @Setter
    private float panY;
    @Getter
    @Setter
    private boolean snapToGrid;

    @Getter
    private Balancer.BalanceMode balanceMode = Balancer.BalanceMode.OUTPUT;
    @Getter
    private boolean opsMode;

    private Balancer.BalanceResult balance = null;
    private Summary summary = null;

    private boolean dirty = true;

    public void markDirty() {
        dirty = true;
    }

    public void setBalanceMode(final Balancer.BalanceMode mode) {
        balanceMode = mode;
        markDirty();
    }

    public void setOpsMode(final boolean opsMode) {
        this.opsMode = opsMode;
        markDirty();
    }

    public void removeNode(final UUID id) {
        nodes.remove(id);
        edges.values()
            .removeIf(e -> e.sourceNodeId.equals(id) || e.targetNodeId.equals(id));
        markDirty();
    }

    public Balancer.BalanceResult balance() {
        if (dirty) {
            balance = Balancer.balance(this, balanceMode, opsMode);
            summary = Summary.compute(balance, this, opsMode);
            dirty = false;
        }
        return balance;
    }

    public Summary summary() {
        balance(); // ensure up-to-date
        return summary;
    }

    public Collection<Node> getNodes() {
        return nodes.values();
    }

    public void addNode(final Node node) {
        nodes.put(node.id, node);
        markDirty();
    }

    public Collection<Edge> getEdges() {
        return edges.values();
    }

    public void addEdge(final Edge edge) {
        edges.put(edge.id, edge);
        markDirty();
    }

    public void removeEdge(final UUID id) {
        edges.remove(id);
        markDirty();
    }

    public void removeGroup(final UUID id) {
        groups.remove(id);
    }

    public Collection<Group> getGroups() {
        return groups.values();
    }

    public Collection<Note> getNotes() {
        return notes.values();
    }
}
