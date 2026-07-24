package com.sbancuz.plannh.gui.node;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.data.flowchart.Port;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.RecipeHandlerRef;
import gregtech.nei.GTNEIDefaultHandler;

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

    public PortWidget(PositionedStack stack, boolean isInput, Port<?> port, RecipeHandlerRef handlerRef) {
        this.stack = stack;
        this.isInput = isInput;

        background(
            new Rectangle().color(isInput ? INPUT_COLOR : OUTPUT_COLOR)
                .hollow());
        hoverOverlay(new Rectangle().color(Color.argb(255, 255, 255, 128)));
        if (Compat.GREGTECH.isLoaded && handlerRef.handler instanceof GTNEIDefaultHandler)
            pos(stack.relx + PORT_OFFSET_X, stack.rely + PORT_OFFSET_Y + 8); // static final
        else pos(stack.relx + PORT_OFFSET_X, stack.rely + PORT_OFFSET_Y);

        tooltipBuilder(t -> t.addFromItem(stack.item));
        addTooltipLine(String.format("%.2f", port.getChance() * 100) + "%");
        if (stack.items.length > 1) tooltipAutoUpdate(true);
    }
}
