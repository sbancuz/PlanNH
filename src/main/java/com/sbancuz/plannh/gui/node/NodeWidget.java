package com.sbancuz.plannh.gui.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.UUID;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.PlannhColors;
import com.sbancuz.plannh.gui.common.CloseButtonWidget;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartTextWidget;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.common.HeaderTextWidget;
import com.sbancuz.plannh.gui.edge.ArrowWidget;

import it.unimi.dsi.fastutil.ints.IntIntPair;
import lombok.Getter;

public class NodeWidget extends FlowchartWidget<NodeWidget, Node> {

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
            .coverChildren();

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

        topRow.child(new CloseButtonWidget(this));
        mainColumn.child(topRow);

        recipeAreaWidget = new RecipeAreaWidget(this);
        mainColumn.child(recipeAreaWidget);

        Flow settingsColumn = FlowchartFlow.column(this)
            .fullWidth()
            .coverChildrenHeight()
            .childPadding(2)
            .crossAxisAlignment(Alignment.CrossAxis.START);

        MachineConfig config = data.getMachineConfig();
        config.getProfile()
            .visibleSettings(new RecipeContext(data.getProperties()), config.getSettings())
            .map(
                settingDef -> FlowchartFlow.row(this)
                    .fullWidth()
                    .coverChildrenHeight()
                    .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                    .child(new FlowchartTextWidget(settingDef.getLabel(), this))
                    .child(settingDef.settingsWidget(config)))
            .forEach(settingsColumn::child);

        mainColumn.child(settingsColumn);

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
