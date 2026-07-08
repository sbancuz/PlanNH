package com.sbancuz.plannh.data.flowchart;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Node2 extends GraphData {

    protected Node2(UUID id) {
        super(id);

    }

    @Override
    public String getType() {
        return "node";
    }
}
