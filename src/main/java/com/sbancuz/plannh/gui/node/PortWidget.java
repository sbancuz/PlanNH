package com.sbancuz.plannh.gui.node;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.data.flowchart.Port;

import codechicken.nei.PositionedStack;

public class PortWidget extends Widget<PortWidget> {

    private static final int INPUT_COLOR = Color.GREEN.main;
    private static final int OUTPUT_COLOR = Color.BLUE.main;
    // needed for proper positioning of ports
    private static final int PORT_OFFSET_X = 1;
    private static final int PORT_OFFSET_Y = -1;

    // save, so we can toggle permutation
    private final PositionedStack stack;
    // this specifies highlight color and dragging behaviour (start/end of arrow)
    private final boolean isInput;
    private final Port<?> port;

    public PortWidget(PositionedStack stack, boolean isInput, Port<?> port, int yShift) {
        this.stack = stack;
        this.isInput = isInput;
        this.port = port;

        background(
            new Rectangle().color(isInput ? INPUT_COLOR : OUTPUT_COLOR)
                .hollow());
        hoverOverlay(new Rectangle().color(Color.argb(255, 255, 255, 128)));
        pos(stack.relx + PORT_OFFSET_X, stack.rely + PORT_OFFSET_Y + yShift);

        tooltipBuilder(t -> t.addFromItem(stack.item).addLine(String.format("%.2f", port.getChance() * 100) + "%"));
        tooltipAutoUpdate(true);
    }
}
