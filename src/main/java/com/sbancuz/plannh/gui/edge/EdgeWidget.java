package com.sbancuz.plannh.gui.edge;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;

public class EdgeWidget extends Widget<EdgeWidget> {

    public EdgeWidget(int index) {
        background(new Rectangle().color(Color.BLACK.main)); // todo add color based on item color
        addTooltipLine(String.valueOf(index));
    }
}
