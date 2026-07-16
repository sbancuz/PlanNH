package com.sbancuz.plannh.gui;

import static com.sbancuz.plannh.data.flowchart.Group.GROUP_MIN_W;

import java.util.Map;
import java.util.UUID;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ColorPickerDialog;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.data.flowchart.Group;

import lombok.Getter;

public class GroupWidget extends FlowchartWidget<GroupWidget, Group> {

    @Getter
    private final GroupAreaWidget areaWidget;

    protected GroupWidget(CanvasWidget canvas, Group data) {
        super(canvas, data);
        areaWidget = new GroupAreaWidget(this);

        coverChildren(GROUP_MIN_W, 0);

        background(
            new DynamicDrawable(
                () -> new Rectangle().hollow(2)
                    .color(data.getColor())));

        data.getChildren()
            .values()
            .forEach(subData -> {
                FlowchartWidget<?, ?> widget = FlowchartWidget.getFlowchartWidgetFromData(canvas, subData);
                widget.dataContainer = data.getChildren();
                areaWidget.child(widget);
            });

        IPanelHandler colorPicker = IPanelHandler
            .simple(canvas.getPanel(), (_, _) -> new ColorPickerDialog(data::setColor, data.getColor(), true), true);

        Flow mainColumn = FlowchartFlow.column()
            .coverChildren(GROUP_MIN_W, 0)
            .collapseDisabledChild();

        Flow topRow = FlowchartFlow.row(this)
            .coverChildrenHeight()
            .fullWidth()
            .background(new DynamicDrawable(() -> new Rectangle().color(data.getColor())));

        topRow.child(new HeaderTextWidget(this, data::getColor));

        Flow buttonRow = FlowchartFlow.row(this)
            .coverChildren()
            .childPadding(2)
            .reverseLayout();

        buttonRow.child(new CloseButtonWidget(this))
            .child(new ToggleButton().value(new BoolValue.Dynamic(data::isCoverChildren, val -> {
                data.setCoverChildren(val);
                if (val) canvas.fitGroupToChildren(data);
                areaWidget.configureCoverChildren();
            }))
                .overlay(
                    IKey.str("CC")
                        .color(Color.WHITE.main))
                .addTooltipLine("Toggle Cover Children"))
            .child(
                new ButtonWidget<>().overlay(GuiTextures.COLOR_WHEEL)
                    .onMousePressed(_ -> {
                        if (!colorPicker.isPanelOpen()) colorPicker.openPanel();
                        else colorPicker.closePanel();
                        return true;
                    }))
            .child(
                new CycleButtonWidget().stateCount(2)
                    .stateOverlay(true, IKey.str("^"))
                    .stateOverlay(false, IKey.str("V"))
                    .value(new BoolValue.Dynamic(data::isCollapsed, val -> {
                        boolean was = data.isCollapsed();
                        data.setCollapsed(val);
                        scheduleResize();
                        canvas.setGroupNodesVisible(data.getId(), !val);
                        if (was && !val) canvas.rebuildNodeWidgets();
                    })));

        topRow.child(buttonRow);

        mainColumn.child(topRow);
        mainColumn.child(areaWidget);

        child(mainColumn);
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
    public boolean onDragStart(int mouseButton) {
        if (mouseButton == 0) {
            final float z = canvas.getGraph()
                .getZoom();
            final int worldMx = Math.round(
                (getContext().getAbsMouseX() - canvas.getArea().x
                    - canvas.getGraph()
                        .getPanX())
                    / z);
            final int worldMy = Math.round(
                (getContext().getAbsMouseY() - canvas.getArea().y
                    - canvas.getGraph()
                        .getPanY())
                    / z);
            if (canvas.isOutputPortHit(worldMx, worldMy)) return false;
        }
        return super.onDragStart(mouseButton);
    }

    @Override
    public void onDrag(int mouseButton, long timeSinceLastClick) {
        final int oldX = data.getX();
        final int oldY = data.getY();
        super.onDrag(mouseButton, timeSinceLastClick);
        final int deltaX = data.getX() - oldX;
        final int deltaY = data.getY() - oldY;
        if (deltaX != 0 || deltaY != 0) {
            canvas.moveGroupNodes(data.getId(), deltaX, deltaY);
        }
    }

    @Override
    public void onDragEnd(boolean successful) {
        super.onDragEnd(successful);
        canvas.recheckMembershipAndFit();
    }

    @Override
    protected Map<UUID, Group> getDefaultContainer() {
        return canvas.getGraph()
            .getGroups();
    }

    public int getMouseGroupX() {
        ModularGuiContext context = getContext();

        return getScreen().getPanelManager()
            .getAllHoveredWidgetsList(false)
            .stream()
            .filter(locatedWidget -> locatedWidget.getElement() == areaWidget)
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
            .filter(locatedWidget -> locatedWidget.getElement() == areaWidget)
            .findFirst()
            .orElseThrow()
            .getTransformationMatrix()
            .unTransformY(context.getAbsMouseX(), context.getAbsMouseY());
    }
}
