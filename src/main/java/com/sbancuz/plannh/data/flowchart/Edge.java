package com.sbancuz.plannh.data.flowchart;

import java.util.UUID;

import javax.annotation.Nonnull;

public class Edge {

    @Nonnull
    public final UUID id;
    /// Source/target
    @Nonnull
    public final UUID sourceId;
    @Nonnull
    public final UUID targetId;
    /// Recipe source/targets inside the nodes
    public int sourceOutputIndex;
    public int targetInputIndex;

    public Edge(final UUID id, final UUID sourceId, final UUID targetId, final int sourceOutputIndex,
        final int targetInputIndex) {
        this.id = id;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.sourceOutputIndex = sourceOutputIndex;
        this.targetInputIndex = targetInputIndex;
    }
}
