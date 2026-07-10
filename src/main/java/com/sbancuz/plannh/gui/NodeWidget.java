package com.sbancuz.plannh.gui;

import java.util.Map;
import java.util.UUID;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.data.flowchart.Node2;

public class NodeWidget extends FlowchartWidget<NodeWidget, Node2> {

    private static UITexture bg = UITexture.builder()
        .location("nei:textures/gui/recipebg.png")
        .imageSize(256, 256)
        .subAreaXYWH(4, 4, 176, 166)
        .adaptable(3)
        .build();

    protected NodeWidget(CanvasWidget canvas, Node2 data) {
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
