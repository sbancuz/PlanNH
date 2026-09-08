package com.sbancuz.plannh.gui.group;

import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.common.FlowchartWidget;

public class SimpleGroupWidget extends GroupWidget<Group> {

    public SimpleGroupWidget(CanvasWidget canvas, Group data) {
        super(canvas, data);
    }

    @Override
    public boolean canAddToGroup(FlowchartWidget<?, ?> widget) {
        return true;
    }
}
