package com.sbancuz.plannh.data.flowchart;

import java.util.UUID;

import com.sbancuz.plannh.gui.node.PortWidget;

import it.unimi.dsi.fastutil.ints.IntIntPair;
import lombok.Getter;

@Getter
public class Edge2 implements IValidated {

    private final UUID id;
    private final UUID sourceNodeId;
    private final UUID targetNodeId;
    private final IntIntPair sourceOutputIndex;
    private final IntIntPair targetInputIndex;

    public Edge2(PortWidget source, PortWidget target) {
        this.id = UUID.randomUUID();
        this.sourceNodeId = source.getNode()
            .getId();
        this.targetNodeId = target.getNode()
            .getId();
        this.sourceOutputIndex = source.getIndex();
        this.targetInputIndex = target.getIndex();
    }

    @Override
    public boolean invalid() {
        return id == null || sourceNodeId == null
            || targetNodeId == null
            || sourceOutputIndex == null
            || targetInputIndex == null;
    }
}
