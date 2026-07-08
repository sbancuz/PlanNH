package com.sbancuz.plannh.data.flowchart;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Graph {

    private final Map<UUID, Node> nodes = new HashMap<>();
    private final Map<UUID, Node2> nodes2 = new HashMap<>();
    private final Map<UUID, Edge> edges = new HashMap<>();
    private final Map<UUID, Note> notes = new HashMap<>();
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
            .removeIf(e -> e.sourceNodeId.equals(id) || e.targetNodeId.equals(id));
    }

    public Balancer.BalanceResult balance() {
        return Balancer.balance(this, balanceMode);
    }
}
