package com.sbancuz.plannh.gui;

import java.util.Map;
import java.util.UUID;

import org.lwjgl.input.Mouse;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IDragResizeable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Group;

public class GroupWidget2 extends FlowchartWidget<GroupWidget2, Group> implements IDragResizeable {

    public static final int GROUP_MIN_W = 300;
    public static final int GROUP_MIN_H = 200;

    protected GroupWidget2(CanvasWidget canvas, Group data) {
        super(canvas, data);

        background(
            new Rectangle().hollow(2)
                .color(data.getColor()));
        size(data.getWidth(), data.getHeight());

        data.getChildren()
            .values()
            .forEach(subData -> {
                FlowchartWidget<?, ?> widget = FlowchartWidget.getFlowchartWidgetFromData(canvas, subData);
                widget.dataContainer = data.getChildren();
                child(widget);
            });

        child(
            FlowchartFlow.row(this)
                .coverChildrenHeight()
                .background(new Rectangle().color(data.getColor()))
                .fullWidth()
                .child(new HeaderTextWidget(this, data.getColor()))
                .child(
                    FlowchartFlow.row(this)
                        .childPadding(2)
                        .coverChildren()
                        .reverseLayout()
                        .child(new CloseButtonWidget(this))
                        .child(new ToggleButton().value(new BoolValue.Dynamic(data::isCoverChildren, val -> {
                            final String before = PlanAPI.undoHistory()
                                .beginEdit(canvas.getGraph());
                            data.setCoverChildren(val);
                            if (data.isCoverChildren()) coverChildren(GROUP_MIN_W, GROUP_MIN_H);
                            else disableCoverChildren();
                            scheduleResize();
                            PlanAPI.undoHistory()
                                .commitEdit(before, canvas.getGraph());
                        }))
                            .overlay(
                                IKey.str("CC")
                                    .color(Color.WHITE.main)))));
    }

    @Override
    public void removeFromGraph() {
        getChildren().stream()
            .filter(w -> w instanceof FlowchartWidget<?, ?>)
            .map(w -> (FlowchartWidget<?, ?>) w)
            .forEach(FlowchartWidget::removeFromGraph);
        super.removeFromGraph();
    }

    @Override
    protected Map<UUID, Group> getDefaultContainer() {
        return canvas.getGraph().groups;
    }

    @Override
    public boolean isCurrentlyResizable() {
        return !data.isCoverChildren();
    }

    @Override
    public int getDragAreaSize() {
        return 10;
    }

    @Override
    public int getMinDragWidth() {
        return Math.max(
            GROUP_MIN_W,
            getChildren().stream()
                .filter(w -> w instanceof FlowchartWidget<?, ?>)
                .mapToInt(w -> w.getArea().rx + w.getArea().width)
                .max()
                .orElse(0));
    }

    @Override
    public int getMinDragHeight() {
        return Math.max(
            GROUP_MIN_H,
            getChildren().stream()
                .filter(w -> w instanceof FlowchartWidget<?, ?>)
                .mapToInt(w -> w.getArea().ry + w.getArea().height)
                .max()
                .orElse(0));
    }

    @Override
    public boolean keepPosOnDragResize() {
        return false;
    }

    // Edge-drag resizing has no end callback: the panel calls onDragResize per moved pixel and
    // swallows the release. Begin the undo bracket on the first tick of a gesture and commit
    // on the first update with the button up.
    private String resizeEditToken;

    @Override
    public void onDragResize() {
        if (resizeEditToken == null) {
            resizeEditToken = PlanAPI.undoHistory()
                .beginEdit(canvas.getGraph());
        }
        IDragResizeable.super.onDragResize();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (resizeEditToken != null && !Mouse.isButtonDown(0)) {
            PlanAPI.undoHistory()
                .commitEdit(resizeEditToken, canvas.getGraph());
            resizeEditToken = null;
        }
    }

    @Override
    public void onResized() {
        Area area = getArea();
        if (data.getWidth() != area.width || data.getHeight() != area.height) {
            data.setX(area.rx);
            data.setY(area.ry);
            data.setWidth(area.width);
            data.setHeight(area.height);
        }
    }

    public int getMouseGroupX() {
        ModularGuiContext context = getContext();

        return getScreen().getPanelManager()
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

        return getScreen().getPanelManager()
            .getAllHoveredWidgetsList(false)
            .stream()
            .filter(locatedWidget -> locatedWidget.getElement() == this)
            .findFirst()
            .orElseThrow()
            .getTransformationMatrix()
            .unTransformY(context.getAbsMouseX(), context.getAbsMouseY());
    }
}
