package com.sbancuz.plannh.gui;

import java.util.Map;
import java.util.UUID;

import com.sbancuz.plannh.data.flowchart.Node2;

public class NodeWidget extends FlowchartWidget<NodeWidget, Node2> {

    protected NodeWidget(CanvasWidget canvas, Node2 data) {
        super(canvas, data);
    }

    @Override
    protected Map<UUID, Node2> getDefaultContainer() {
        return canvas.getGraph()
            .getNodes2();
    }
}
