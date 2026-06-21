package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.api.widget.IDragResizeable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;
import com.sbancuz.plannh.data.flowchart.Group;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

public class GroupWidget2 extends FlowchartWidget<GroupWidget2, Group> implements IDragResizeable {
    protected GroupWidget2(CanvasWidget canvas, Group data) {
        super(canvas, data);

        background(new Rectangle().hollow().color(data.getColor()));
        size(data.getW(), data.getH());
    }

    @Override
    protected Map<UUID, Group> getDefaultContainer() {
        return canvas.getGraph().groups;
    }
}
