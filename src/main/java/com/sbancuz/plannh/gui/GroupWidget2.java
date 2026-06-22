package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.api.widget.IDragResizeable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.sbancuz.plannh.data.flowchart.Group;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class GroupWidget2 extends FlowchartWidget<GroupWidget2, Group> implements IDragResizeable {

    public static final int GROUP_MIN_W = 300;
    public static final int GROUP_MIN_H = 200;

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
    public int getDragAreaSize() {
        return 10;
    }

    @Override
    public int getMinDragWidth() {
        return GROUP_MIN_W;
    }

    @Override
    public int getMinDragHeight() {
        return GROUP_MIN_H;
    }

    @Override
    public boolean keepPosOnDragResize() {
        return false;
    }

    @Override
    public void onResized() {
        Area area = getArea();
        if(data.getWidth() != area.width || data.getHeight() != area.height){
            data.setX(area.rx);
            data.setY(area.ry);
            data.setWidth(area.width);
            data.setHeight(area.height);
        }
    }

    public int getMouseGroupX() {
        ModularGuiContext context = getContext();

        return getScreen()
            .getPanelManager()
            .getAllHoveredWidgetsList(false)
            .stream()
            .filter(locatedWidget -> locatedWidget.getElement() == this)
            .findFirst()
            .orElseThrow()
            .getTransformationMatrix()
            .unTransformX(context.getAbsMouseX(), context.getAbsMouseY());
    }

    public int getMouseGroupY() {
        ModularGuiContext context = getContext();

        return getScreen()
            .getPanelManager()
            .getAllHoveredWidgetsList(false)
            .stream()
            .filter(locatedWidget -> locatedWidget.getElement() == this)
            .findFirst()
            .orElseThrow()
            .getTransformationMatrix()
            .unTransformY(context.getAbsMouseX(), context.getAbsMouseY());
    }
}
