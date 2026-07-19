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

    public boolean hasIncomingEdge(final UUID nodeId, final int inputIndex) {
        for (final Edge edge : edges.values()) {
            if (edge.targetNodeId.equals(nodeId) && edge.targetInputIndex == inputIndex) return true;
        }
        return false;
    }

    /**
     * First input port of {@code dst} that accepts {@code src}'s given output, preferring ports
     * that nothing feeds yet; -1 when none is compatible. The single auto-wiring policy for both
     * drop-on-node-body connects and NEI lookup auto-connects.
     */
    public int findCompatibleInput(final Node src, final int srcOutIdx, final Node dst) {
        if (src == dst || srcOutIdx < 0 || srcOutIdx >= src.outputs.size()) return -1;
        final Port<?> out = src.outputs.get(srcOutIdx);
        int firstCompatible = -1;
        for (int i = 0; i < dst.inputs.size(); i++) {
            if (!out.canConnect(dst.inputs.get(i))) continue;
            if (firstCompatible < 0) firstCompatible = i;
            if (!hasIncomingEdge(dst.id, i)) return i;
        }
        return firstCompatible;
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
