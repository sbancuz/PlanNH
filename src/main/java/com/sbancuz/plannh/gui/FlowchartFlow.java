package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.widgets.layout.Flow;

public class FlowchartFlow extends Flow implements IFlowchartDraggable {

    private final FlowchartWidget<?, ?> parent;

    private FlowchartFlow(GuiAxis axis, FlowchartWidget<?, ?> parent) {
        super(axis);
        this.parent = parent;
    }

    public static FlowchartFlow row(FlowchartWidget<?, ?> parent) {
        return new FlowchartFlow(GuiAxis.X, parent);
    }

    public static FlowchartFlow column(FlowchartWidget<?, ?> parent) {
        return new FlowchartFlow(GuiAxis.Y, parent);
    }

    @Override
    public FlowchartWidget<?, ?> getFlowchartParent() {
        return parent;
    }
}
