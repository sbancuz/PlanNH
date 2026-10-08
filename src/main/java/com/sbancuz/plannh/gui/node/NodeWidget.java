package com.sbancuz.plannh.gui.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.PlannhColors;
import com.sbancuz.plannh.gui.common.CloseButtonWidget;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.common.HeaderTextWidget;
import com.sbancuz.plannh.gui.edge.ArrowWidget;
import com.sbancuz.plannh.gui.tooltips.TooltipBuilder;

import it.unimi.dsi.fastutil.ints.IntIntPair;
import lombok.Getter;

public class NodeWidget extends FlowchartWidget<NodeWidget, Node> {

    private static final String SETTINGS_LANG = "plannh.gui.node.settings";
    private static final String TARGET_LANG = "plannh.gui.node.target.rows";
    private static final String THROUGHPUT_LANG = "plannh.gui.node.throughput.rows";

    /** The fold toggles, square for the header row they sit in. */
    private static final int FOLD_BUTTON = 12;

    private final RecipeAreaWidget recipeAreaWidget;
    private final CycleButtonWidget settingsFold;
    private final CycleButtonWidget targetFold;
    private final CycleButtonWidget throughputFold;
    private boolean lastSettingsOpen;
    private boolean lastTargetOpen;
    private boolean lastThroughputOpen;

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

        Flow mainColumn = FlowchartFlow.col(this)
            .coverChildren()
            .collapseDisabledChild()
            .childPadding(4);

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

        // The three folds, told apart by their icon and dimmed when they are not showing, so which rows
        // are on screen is readable from the header alone. Each hint names what the click will do, so
        // every tooltip turns over with its fold rather than answering a question already changed. The
        // chip is the throughput fold: a chart reads as the target, and a gear cannot be mistaken for
        // either of the two.
        throughputFold = fold(GuiTextures.PROCESSOR, THROUGHPUT_LANG, data::isThroughputOpen, data::setThroughputOpen);
        targetFold = fold(GuiTextures.GRAPH, TARGET_LANG, data::isTargetOpen, data::setTargetOpen);
        settingsFold = fold(GuiTextures.GEAR, SETTINGS_LANG, data::isSettingsOpen, data::setSettingsOpen);
        topRow.child(throughputFold);
        topRow.child(targetFold);
        topRow.child(settingsFold);
        topRow.child(new CloseButtonWidget(this));
        mainColumn.child(topRow);

        recipeAreaWidget = new RecipeAreaWidget(this);
        mainColumn.child(recipeAreaWidget);
        mainColumn.child(new ThroughputInfoWidget(this));

        mainColumn.child(new ConfigurationAreaWidget(this));

        child(mainColumn);
    }

    private CycleButtonWidget fold(final UITexture icon, final String langKey, final BooleanSupplier open,
        final Consumer<Boolean> set) {
        return new CycleButtonWidget().size(FOLD_BUTTON)
            .stateCount(2)
            .stateOverlay(true, icon.asIcon())
            .stateOverlay(
                false,
                icon.withColorOverride(PlannhColors.TEXT_DARK.getColor())
                    .asIcon())
            .value(new BoolValue.Dynamic(open, set::accept))
            .tooltipBuilder(
                tooltip -> TooltipBuilder.create(tooltip)
                    .langRows(langKey, langKey + (open.getAsBoolean() ? ".hide_hint" : ".show_hint"))
                    .flush());
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        final boolean target = data.isTargetOpen();
        if (target != lastTargetOpen) {
            lastTargetOpen = target;
            if (targetFold != null) targetFold.markTooltipDirty();
        }

        final boolean settings = data.isSettingsOpen();
        if (settings != lastSettingsOpen) {
            lastSettingsOpen = settings;
            if (settingsFold != null) settingsFold.markTooltipDirty();
        }

        final boolean throughput = data.isThroughputOpen();
        if (throughput != lastThroughputOpen) {
            lastThroughputOpen = throughput;
            if (throughputFold != null) throughputFold.markTooltipDirty();
        }
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

    /** How the chart's last solve treated this machine, or null while it has never been solved. */
    public Balancer.NodeBalance balance() {
        return canvas.getGraph()
            .balance()
            .nodeBalances()
            .get(data.getId());
    }

    /** The mode the whole chart is being solved in, which is what decides which pins exist at all. */
    public BalanceMode balanceMode() {
        return canvas.getGraph()
            .getBalanceMode();
    }
}
