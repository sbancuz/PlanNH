package com.sbancuz.plannh.gui.group;

import static com.sbancuz.plannh.data.flowchart.Group.GROUP_MIN_W;

import java.util.SortedMap;
import java.util.UUID;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ColorPickerDialog;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.common.CloseButtonWidget;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.common.HeaderTextWidget;
import com.sbancuz.plannh.data.flowchart.MachineGroup;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;

import lombok.Getter;

public class GroupWidget extends FlowchartWidget<GroupWidget, Group> {

    @Getter
    private final GroupAreaWidget areaWidget;

    public GroupWidget(CanvasWidget canvas, Group data) {
        super(canvas, data);
        areaWidget = new GroupAreaWidget(this);

        coverChildren(GROUP_MIN_W, 0);

        final int borderWidth = data instanceof MachineGroup ? 4 : 2;
        background(
            new DynamicDrawable(
                () -> new Rectangle().hollow(borderWidth)
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

        Flow topRow = FlowchartFlow.row(this)
            .coverChildrenHeight()
            .fullWidth()
            .childPadding(4)
            .background(new DynamicDrawable(() -> new Rectangle().color(data.getColor())));

        // The badge, and the doubled border below it, are what tell a machine group from an
        // ordinary one at a glance: the two behave differently and are easy to mistake otherwise.
        if (data instanceof MachineGroup) {
            topRow.child(
                new TextWidget<>(IKey.lang("plannh.gui.group.machine_badge")).color(Color.WHITE.main)
                    .textAlign(Alignment.CenterLeft)
                    .addTooltipLine(IKey.lang("plannh.gui.group.machine_group_tooltip")));
        }
        topRow.child(new HeaderTextWidget(this, data::getColor));

        Flow buttonRow = FlowchartFlow.row(this)
            .coverChildren()
            .childPadding(2)
            .reverseLayout();

        buttonRow.child(new CloseButtonWidget(this));
        if (data instanceof final MachineGroup machineGroup) {
            buttonRow.child(capacityButton(machineGroup, "+", 1))
                .child(capacityButton(machineGroup, "-", -1));
        }
        buttonRow.child(new ToggleButton().value(new BoolValue.Dynamic(data::isCoverChildren, val -> {
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

        // TODO aggregate these if data instanceof MGs
        if (data instanceof final MachineGroup machineGroup) topRow.child(loadLabel(machineGroup));
        topRow.child(buttonRow);

        mainColumn.child(topRow);
        mainColumn.child(areaWidget);

        child(mainColumn);
    }

    /**
     * Steps the pool's capacity, floored at zero, which is the group's "as many machines as it
     * takes" and the state every chart starts in. The capacity is a solve input, so a step is an
     * edit like any other: recorded for undo and version-bumped so the chart re-balances under it.
     */
    private ButtonWidget<?> capacityButton(final MachineGroup group, final String label, final int step) {
        return new ButtonWidget<>().overlay(
            IKey.str(label)
                .color(Color.WHITE.main))
            .onMousePressed(_ -> {
                PlanAPI.recordEdit(canvas.getGraph(), () -> {
                    group.setMachineCapacity(Math.max(0, group.getMachineCapacity() + step));
                    resolve();
                });
                return true;
            })
            .addTooltipLine(IKey.lang("plannh.gui.group.machine_capacity"));
    }

    /** Marks the chart for a fresh solve; a pool's capacity and membership are model inputs. */
    private void resolve() {
        canvas.getGraph()
            .markDirty();
    }

    /**
     * The pool's load: the machine counts of the framed nodes, summed. A node's count is already
     * machine time - a recipe filling a third of a machine reads 0.33 - so the sum is how much of
     * the one machine the group's recipes ask for between them. Every member runs the same handler
     * under the same settings, which is what makes the sum a single machine's worth of work rather
     * than an addition of unlike things.
     */
    private double machineLoad() {
        final Graph graph = canvas.getGraph();
        final BalanceResult balance = graph.balance();
        if (balance == null) return 0;
        double load = 0;
        for (final UUID nodeId : getData().getNodeIds()) {
            final Balancer.NodeBalance nb = balance.nodeBalances()
                .get(nodeId);
            if (nb == null || nb.operations() <= 0) continue;
            load += nb.operations();
        }
        return load;
    }

    /** The machine the group stands for, from any member; empty until a node is framed. */
    private String machineName() {
        final Graph graph = canvas.getGraph();
        for (final UUID nodeId : getData().getNodeIds()) {
            final Node node = graph.nodes.get(nodeId);
            if (node != null) return node.machineName;
        }
        return "";
    }

    /**
     * The load next to the group's name, against the capacity when one is set. Machine names are as
     * long as GregTech feels like and the row already holds the name field and the buttons, so the
     * header carries the number and the machine is named in the tooltip, which is pinned below the
     * row - the default position sits beside the widget and trims to whatever screen width is left
     * there. Empty for a group with no solved nodes, which collapses the widget away.
     */
    private TextWidget<?> loadLabel(final MachineGroup group) {
        return new TextWidget<>(IKey.dynamic(() -> {
            final double load = machineLoad();
            if (load <= 0) return "";
            final String used = "×" + GuiHelper.formatCount(load);
            return group.getMachineCapacity() > 0 ? used + " / " + group.getMachineCapacity() : used;
        })).color(PlannhColors.SUMMARY_TEXT_MUTED.getColor())
            .textAlign(Alignment.CenterRight)
            .tooltipPos(RichTooltip.Pos.BELOW)
            .tooltipAutoUpdate(true)
            .tooltipDynamic(tooltip -> {
                final double load = machineLoad();
                if (load <= 0) return;
                tooltip.add("×" + GuiHelper.formatCount(load) + " " + machineName())
                    .newLine();
            });
    }

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
    protected SortedMap<UUID, Group> getDefaultContainer() {
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
