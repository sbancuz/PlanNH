package com.sbancuz.plannh.data.flowchart;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Graph {

    private final Map<UUID, Node> nodes = new HashMap<>();
    private final Map<UUID, Edge> edges = new HashMap<>();
    private final Map<UUID, Note> notes = new HashMap<>();
    private final Map<UUID, Step> steps = new HashMap<>();
    private final Map<UUID, Group> groups = new HashMap<>();

    private float zoom = 1f;
    private float panX;
    private float panY;
    private String name;

    private Balancer.BalanceMode balanceMode = Balancer.BalanceMode.BACKWARD;

    public Graph(String name) {
        this.name = name;
    }

    public void removeNode(final UUID id) {
        nodes.remove(id);
        edges.values()
            .removeIf(e -> id.equals(e.sourceId) || id.equals(e.targetId));
    }

    public Balancer.BalanceResult balance() {
        return Balancer.balance(this, balanceMode);
    }

    public Iterable<FlowData> getFlowParticipants() {
        return () -> new Iterator<>() {

            final Iterator<Node> ni = nodes.values()
                .iterator();
            final Iterator<Step> si = steps.values()
                .iterator();

            @Override
            public boolean hasNext() {
                return ni.hasNext() || si.hasNext();
            }

            @Override
            public FlowData next() {
                return ni.hasNext() ? ni.next() : si.next();
            }
        };
    }

    @Nullable
    public FlowData getFlowParticipant(final UUID id) {
        final Node n = nodes.get(id);
        return n != null ? n : steps.get(id);
    }
}
