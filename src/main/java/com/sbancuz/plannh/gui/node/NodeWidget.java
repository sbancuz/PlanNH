package com.sbancuz.plannh.gui.node;

import java.util.Map;
import java.util.UUID;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.data.flowchart.Node2;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.PlannhColors;
import com.sbancuz.plannh.gui.common.CloseButtonWidget;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.common.HeaderTextWidget;

public class NodeWidget extends FlowchartWidget<NodeWidget, Node2> {

    private static final UITexture bg = UITexture.builder()
        .location("nei:textures/gui/recipebg.png")
        .imageSize(256, 256)
        .subAreaXYWH(4, 4, 176, 166)
        .adaptable(3)
        .build();

    public NodeWidget(CanvasWidget canvas, Node2 data) {
        super(canvas, data);
        coverChildren();
        background(bg);
        padding(5);

        Flow mainColumn = FlowchartFlow.column(this)
            .coverChildren();

        Flow topRow = FlowchartFlow.row(this)
            .coverChildrenHeight()
            .fullWidth()
            .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN);

        topRow.child(new HeaderTextWidget(this, PlannhColors.titleColor(data.getMachineName())))
            .child(new CloseButtonWidget(this));

        mainColumn.child(topRow)
            .child(new RecipeAreaWidget(this));

        child(mainColumn);
    }

    @Override
    protected Map<UUID, Node2> getDefaultContainer() {
        return canvas.getGraph()
            .getNodes2();
    }
}
