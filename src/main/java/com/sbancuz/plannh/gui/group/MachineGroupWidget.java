package com.sbancuz.plannh.gui.group;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.MachineGroup;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.GuiHelper;
import com.sbancuz.plannh.gui.PlannhColors;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.node.NodeWidget;

public class MachineGroupWidget extends GroupWidget<MachineGroup> {

    @Nullable
    private Node mainNode;

    public MachineGroupWidget(CanvasWidget canvas, MachineGroup data) {
        super(canvas, data);
        mainNode = data.getChildren()
            .values()
            .stream()
            .filter(graphData -> graphData instanceof Node)
            .map(graphData -> (Node) graphData)
            .findFirst()
            .orElse(null);
    }

    @Override
    public boolean canAddToGroup(FlowchartWidget<?, ?> widget) {
        return switch (widget.getData()) {
            case Group _ -> false;
            case Node node -> acceptsNode(node);
            default -> true;
        };
    }

    /**
     * Whether a machine group would accept this node. The group is one machine, so every member
     * has to be the same one: identity is the NEI recipe handler rather than the machine's display
     * name, which a player can rewrite.
     */
    public boolean acceptsNode(final Node node) {
        if (mainNode == null) {
            mainNode = node;
            return true;
        }

        return node.getMachineName()
            .equals(mainNode.getMachineName());
    }

    @Override
    public void joinGroup(FlowchartWidget<?, ?> widget) {
        super.joinGroup(widget);

        if (widget instanceof NodeWidget nodeWidget && nodeWidget.getData() != mainNode) nodeWidget.getData()
            .getMachineConfig()
            .copySettingsFrom(mainNode.getMachineConfig());
    }

    @Override
    public void leaveGroup(FlowchartWidget<?, ?> widget) {
        mainNode = data.getChildren()
            .values()
            .stream()
            .filter(graphData -> graphData instanceof Node)
            .map(graphData -> (Node) graphData)
            .findFirst()
            .orElse(null);
    }

    @Override
    protected Flow getTopRow() {
        return super.getTopRow().child(
            0,
            new TextWidget<>(IKey.lang("plannh.gui.group.machine_badge")).color(Color.WHITE.main)
                .textAlign(Alignment.CenterLeft)
                .addTooltipLine(IKey.lang("plannh.gui.group.machine_group_tooltip")));
    }

    @Override
    protected Flow getButtonRow(IPanelHandler colorPicker) {
        return super.getButtonRow(colorPicker).child(capacityButton("+", 1))
            .child(capacityButton("-", -1))
            .child(loadLabel());
    }

    @Override
    protected int getBorderWidth() {
        return 4;
    }

    /**
     * Steps the pool's capacity, floored at zero, which is the group's "as many machines as it
     * takes" and the state every chart starts in. The capacity is a solve input, so a step is an
     * edit like any other: recorded for undo and version-bumped so the chart re-balances under it.
     */
    private ButtonWidget<?> capacityButton(final String label, final int step) {
        return new ButtonWidget<>().overlay(
            IKey.str(label)
                .color(Color.WHITE.main))
            .onMousePressed(_ -> {
                PlanAPI.recordEdit(canvas.getGraph(), () -> {
                    data.setMachineCapacity(Math.max(0, data.getMachineCapacity() + step));
                    resolve();
                });
                return true;
            })
            .addTooltipLine(IKey.lang("plannh.gui.group.machine_capacity"));
    }

    /**
     * The load next to the group's name, against the capacity when one is set. Machine names are as
     * long as GregTech feels like and the row already holds the name field and the buttons, so the
     * header carries the number and the machine is named in the tooltip, which is pinned below the
     * row - the default position sits beside the widget and trims to whatever screen width is left
     * there. Empty for a group with no solved nodes, which collapses the widget away.
     */
    private TextWidget<?> loadLabel() {
        return new TextWidget<>(IKey.dynamic(() -> {
            final double load = machineLoad();
            if (load <= 0) return "";
            final String used = "×" + GuiHelper.formatCount(load);
            return data.getMachineCapacity() > 0 ? used + " / " + data.getMachineCapacity() : used;
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

    /**
     * Marks the chart for a fresh solve; a pool's capacity and membership are model inputs.
     */
    private void resolve() {
        canvas.getGraph()
            .bumpVersion();
    }

    /**
     * The pool's load: the machine counts of the framed nodes, summed. A node's count is already
     * machine time - a recipe filling a third of a machine reads 0.33 - so the sum is how much of
     * the one machine the group's recipes ask for between them. Every member runs the same handler
     * under the same settings, which is what makes the sum a single machine's worth of work rather
     * than an addition of unlike things.
     */
    private double machineLoad() {
        // todo after balancer connection
        /*
         * final Graph graph = canvas.getGraph();
         * final BalanceResult balance = graph.balance();
         * if (balance == null) return 0;
         * double load = 0;
         * for (final UUID nodeId : getData().getNodeIds()) {
         * final Balancer.NodeBalance nb = balance.nodeBalances()
         * .get(nodeId);
         * if (nb == null || nb.operations() <= 0) continue;
         * load += nb.operations();
         * }
         * return load;
         */
        return 1;
    }

    /**
     * The machine the group stands for, from any member; empty until a node is framed.
     */
    private String machineName() {
        return mainNode != null ? mainNode.getMachineName() : "";
    }
}
