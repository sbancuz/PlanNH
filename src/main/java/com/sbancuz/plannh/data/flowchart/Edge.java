package com.sbancuz.plannh.data.flowchart;

import java.util.UUID;

public class Edge {

    public final UUID id;
    /// Nodes
    public final UUID sourceNodeId;
    public final UUID targetNodeId;
    /// Recipe source/targets inside the nodes
    public int sourceOutputIndex;
    public int targetInputIndex;

    public Edge(UUID id, UUID sourceNodeId, UUID targetNodeId, int sourceOutputIndex, int targetInputIndex) {
        this.id = id;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.sourceOutputIndex = sourceOutputIndex;
        this.targetInputIndex = targetInputIndex;
    }
}
