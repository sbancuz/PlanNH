package com.sbancuz.plannh.gui.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.PlannhColors;
import com.sbancuz.plannh.gui.common.CloseButtonWidget;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.common.HeaderTextWidget;
import com.sbancuz.plannh.gui.edge.ArrowWidget;

import it.unimi.dsi.fastutil.ints.IntIntPair;
import lombok.Getter;

/**
 * A machine on the canvas.
 *
 * <p>
 * <b>Geometry contract.</b> Auto-layout reads this widget's box, so three things about it are load
 * bearing and worth writing down rather than rediscovering:
 *
 * <ul>
 * <li><b>Size comes from {@code getArea()},</b> and only after a MUI2 layout pass has resolved
 * {@code coverChildren()} ({@code :49}). There is no {@code worldWidth}/{@code worldHeight}, and a
 * machine's height moves when the settings list opens or the throughput line rebuilds — so a box read
 * during a click handler may be one or two frames stale. MUI2 also culls off-viewport widgets, and
 * {@code SettingsList} deliberately does not cap its height on the first frame, so a legitimate
 * machine can report a zero box. Callers need a fallback.</li>
 * <li><b>Pins are recipe-driven, not indexed.</b> A {@code PortWidget} positions itself at
 * {@code pos(stack.relx + 1, stack.rely - 1 + yShift)} relative to {@code RecipeAreaWidget}
 * ({@code PortWidget:98}), where {@code yShift} comes from the GTNEI handler. <b>Pin Y is not a
 * function of port index</b> — it is wherever the ingredient sits in NEI's grid. The formula the
 * deleted {@code RecipeNodeWidget} used, {@code (i + 1) * 18 + 10}, describes a widget that no longer
 * exists.</li>
 * <li><b>The port index is a pair.</b> {@code PortWidget.getIndex()} is an {@code IntIntPair} of
 * {@code (portIndex, stackIndex)}: one logical port can carry several stacks, each its own pin. The
 * arrow router keys on the whole pair, so one relation's two ends can be any stack of any port.</li>
 * </ul>
 */
public class NodeWidget extends FlowchartWidget<NodeWidget, Node> {

    private static final String SETTINGS_LANG = "plannh.gui.node.settings";

    private final RecipeAreaWidget recipeAreaWidget;
    @Getter
    private final List<ArrowWidget> arrowWidgets = new ArrayList<>();

    private static final UITexture bg = UITexture.builder()
        .location("nei:textures/gui/recipebg.png")
        .imageSize(256, 256)
        .subAreaXYWH(4, 4, 176, 166)
        .adaptable(3)
        .build();

    public NodeWidget(CanvasWidget canvas, Node data) {
        super(canvas, data);
        canvas.getNodeWidgets2()
            .put(data.getId(), this);

        coverChildren();
        background(bg);
        padding(5);

        Flow mainColumn = FlowchartFlow.column(this)
            .coverChildren()
            .collapseDisabledChild();

        Flow topRow = FlowchartFlow.row(this)
            .coverChildrenHeight()
            .childPadding(4)
            .fullWidth()
            .background(new Rectangle().color(PlannhColors.titleColor(data.getMachineName())))
            .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN);

        topRow.child(
            new HeaderTextWidget(this, PlannhColors.titleColor(data.getMachineName())).setScale(1)
                .background()
                .setTextColor(Color.BLACK.main));

        // The settings fold, the way a summary section folds: two states driven off the node, and
        // the body below is switched off rather than taken out of the tree.
        topRow.child(
            new CycleButtonWidget().size(12)
                .stateCount(2)
                .stateOverlay(true, IKey.str("^"))
                .stateOverlay(false, IKey.str("V"))
                .value(new BoolValue.Dynamic(data::isSettingsOpen, data::setSettingsOpen))
                .tooltipBuilder(
                    t -> t.addLine(IKey.lang(SETTINGS_LANG))
                        .addLine(
                            IKey.lang(() -> SETTINGS_LANG + (data.isSettingsOpen() ? ".hide_hint" : ".show_hint")))));
        topRow.child(new CloseButtonWidget(this));
        mainColumn.child(topRow);

        recipeAreaWidget = new RecipeAreaWidget(this);
        mainColumn.child(recipeAreaWidget);
        mainColumn.child(new ThroughputInfoWidget(this));
        mainColumn.child(new SettingsList(this));

        child(mainColumn);
    }

    @Override
    public boolean isObstacle() {
        return true;
    }

    @Override
    protected SortedMap<UUID, Node> getDefaultContainer() {
        return canvas.getGraph()
            .getNodes();
    }

    @Override
    public void removeFromGraph() {
        super.removeFromGraph();
        // The graph's own removal, not just the widget's: it also takes this node's edges with it.
        canvas.getGraph()
            .removeNode(data.getId());
        canvas.getNodeWidgets2()
            .remove(data.getId());
        arrowWidgets.forEach(ArrowWidget::removeFromGraph);
    }

    public Map<IntIntPair, PortWidget> getPortWidgets(boolean isInput) {
        return recipeAreaWidget.getPortWidgets(isInput);
    }

    public PortWidget getPortWidget(IntIntPair index, boolean isInput) {
        return getPortWidgets(isInput).get(index);
    }

}
