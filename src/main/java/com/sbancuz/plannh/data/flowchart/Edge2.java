package com.sbancuz.plannh.data.flowchart;

import java.util.UUID;

import it.unimi.dsi.fastutil.ints.IntIntPair;
import lombok.Getter;

@Getter
public class Edge2 {

    private final UUID id;
    private final UUID sourceNodeId;
    private final UUID targetNodeId;
    private final IntIntPair sourceOutputIndex;
    private final IntIntPair targetInputIndex;

    public Edge2(UUID sourceNodeId, UUID targetNodeId, IntIntPair sourceOutputIndex, IntIntPair targetInputIndex) {
        this.id = UUID.randomUUID();
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.sourceOutputIndex = sourceOutputIndex;
        this.targetInputIndex = targetInputIndex;
    }
}
