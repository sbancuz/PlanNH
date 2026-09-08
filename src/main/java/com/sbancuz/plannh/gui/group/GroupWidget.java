package com.sbancuz.plannh.gui.group;

import static com.sbancuz.plannh.data.flowchart.Group.GROUP_MIN_W;

import java.util.List;
import java.util.SortedMap;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
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
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.MachineGroup;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.common.CloseButtonWidget;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.common.HeaderTextWidget;

public abstract class GroupWidget<G extends Group> extends FlowchartWidget<GroupWidget<G>, G> {

    protected final GroupAreaWidget areaWidget;
    private List<FlowchartWidget<?, ?>> dragStartIntersect;

    public static <G extends Group> GroupWidget<?> of(CanvasWidget canvas, G group) {
        if (group instanceof MachineGroup machineGroup) return new MachineGroupWidget(canvas, machineGroup);
        return new SimpleGroupWidget(canvas, group);
    }

    protected GroupWidget(CanvasWidget canvas, G data) {
        super(canvas, data);
        areaWidget = new GroupAreaWidget(this);

        coverChildren(GROUP_MIN_W, 0);

        background(
            new DynamicDrawable(
                () -> new Rectangle().hollow(getBorderWidth())
                    .color(data.getColor())));

        data.getChildren()
            .values()
            .forEach(subData -> {
                FlowchartWidget<?, ?> widget = FlowchartWidget.getFlowchartWidgetFromData(canvas, subData);
                widget.setDataContainer(data.getChildren());
                areaWidget.child(widget);
            });

        IPanelHandler colorPicker = IPanelHandler.simple(
            canvas.getPanel(),
            (_, _) -> new ColorPickerDialog(
                color -> PlanAPI.recordEdit(canvas.getGraph(), () -> data.setColor(color)),
                data.getColor(),
                true),
            true);

        Flow mainColumn = FlowchartFlow.column(this)
            .coverChildren(GROUP_MIN_W, 0)
            .collapseDisabledChild();

        Flow topRow = getTopRow();
        Flow buttonRow = getButtonRow(colorPicker);

        topRow.child(buttonRow);

        mainColumn.child(topRow);
        mainColumn.child(areaWidget);

        child(mainColumn);
    }

    protected int getBorderWidth() {
        return 2;
    }

    protected Flow getTopRow() {
        return FlowchartFlow.row(this)
            .coverChildrenHeight()
            .fullWidth()
            .childPadding(4)
            .background(new DynamicDrawable(() -> new Rectangle().color(data.getColor())))
            .child(new HeaderTextWidget(this, data::getColor));
    }

    protected Flow getButtonRow(IPanelHandler colorPicker) {
        return FlowchartFlow.row(this)
            .coverChildren()
            .childPadding(2)
            .reverseLayout()
            .child(new CloseButtonWidget(this))
            .child(new ToggleButton().value(new BoolValue.Dynamic(data::isCoverChildren, val -> {
                data.setCoverChildren(val);
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
                    })
                    .addTooltipLine("Open Color Picker"))
            .child(
                new CycleButtonWidget().stateCount(2)
                    .stateOverlay(true, IKey.str("^"))
                    .stateOverlay(false, IKey.str("V"))
                    .value(new BoolValue.Dynamic(data::isCollapsed, val -> {
                        PlanAPI.recordEdit(canvas.getGraph(), () -> data.setCollapsed(val));
                        scheduleResize();
                    })));
    }

    @Override
    public boolean onDragStartWithOffset(int mouseButton, int x, int y) {
        if (super.onDragStartWithOffset(mouseButton, x, y)) {
            dragStartIntersect = canvas.getFlowchartWidgets()
                .values()
                .stream()
                .filter(
                    widget -> widget.getArea()
                        .intersects(getArea()))
                .toList();
            return true;
        }
        return false;
    }

    @Override
    public boolean canDropHere(int x, int y, @Nullable IWidget widget) {
        // groups can also intersect with their children
        return canvas.isMouseInsideCanvas() && canvas.getFlowchartWidgets()
            .values()
            .stream()
            .filter(
                f -> f.getArea()
                    .intersects(getArea()))
            .allMatch(
                f -> f == this || f instanceof GroupWidget<?>groupWidget && groupWidget.canAddToGroup(this)
                    || dragStartIntersect.contains(f));
    }

    public abstract boolean canAddToGroup(FlowchartWidget<?, ?> widget);

    public void joinGroup(FlowchartWidget<?, ?> widget) {
        areaWidget.child(widget);
        widget.setDataContainer(data.getChildren());
    }

    public void leaveGroup(FlowchartWidget<?, ?> widget) {}

    @Override
    public void removeFromGraph() {
        areaWidget.getChildren()
            .stream()
            .filter(w -> w instanceof FlowchartWidget<?, ?>)
            .map(w -> (FlowchartWidget<?, ?>) w)
            .forEach(FlowchartWidget::removeFromGraph);
        super.removeFromGraph();
    }

    @Override
    protected SortedMap<UUID, ? super G> getDefaultContainer() {
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

    @Override
    public boolean isObstacle() {
        return false;
    }
}
