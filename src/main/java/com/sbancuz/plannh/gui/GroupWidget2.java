package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.api.widget.IDragResizeable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.sbancuz.plannh.data.flowchart.Group;

import java.util.Map;
import java.util.UUID;

public class GroupWidget2 extends FlowchartWidget<GroupWidget2, Group> implements IDragResizeable {
    protected GroupWidget2(CanvasWidget canvas, Group data) {
        super(canvas, data);

        background(new Rectangle().hollow(2).color(data.getColor()));
        size(data.getWidth(), data.getHeight());

        data.getChildren().values().forEach(subData -> {
            FlowchartWidget<?, ?> widget = FlowchartWidget.getFlowchartWidgetFromData(canvas, subData);
            widget.dataContainer = data.getChildren();
            child(widget);
        });
    }

    @Override
    protected Map<UUID, Group> getDefaultContainer() {
        return canvas.getGraph().groups;
    }

    @Override
    public boolean keepPosOnDragResize() {
        return false;
    }

    @Override
    public void onResized() {
        Area area = getArea();
//        data.setX(area.x);
//        data.setY(area.y);
        data.setWidth(area.width);
        data.setHeight(area.height);
    }

    public int getMouseGroupX() {
        return getContext().getAbsMouseX(); // - getArea().x;
    }

    public int getMouseGroupY() {
        return getContext().getAbsMouseX(); // - getArea().y;
    }
}
