package com.sbancuz.plannh.gui.node;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.RecipeHandlerRef;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.gtnewhorizons.modularui.api.math.Size;
import com.sbancuz.plannh.mixins.GTNEIDefaultHandlerAccessor;
import gregtech.nei.GTNEIDefaultHandler;

import java.awt.Point;
import java.util.List;
import java.util.Objects;

public class PortWidget extends Widget<PortWidget> {

    private static final int INPUT_COLOR = Color.GREEN.main;
    private static final int OUTPUT_COLOR = Color.BLUE.main;

    private final RecipeHandlerRef handlerRef;
    // this specifies highlight color and dragging behaviour (start/end of arrow)
    private final boolean isInput;

    public PortWidget(RecipeHandlerRef handlerRef, boolean isInput, PositionedStack stack) {
        this.handlerRef = handlerRef;
        this.isInput = isInput;

        Point offset = new Point(1, -1);
        if (handlerRef.handler instanceof GTNEIDefaultHandler)
            offset.y = 7;

        background(new Rectangle().color(isInput? INPUT_COLOR : OUTPUT_COLOR).hollow());
        hoverOverlay(new Rectangle().color(Color.argb(255, 255, 255, 128)));
        pos(stack.relx + offset.x, stack.rely + offset.y);
        tooltip(t -> t.addFromItem(stack.item));
        addTooltipLine((float) stack.getChance() / 100 + "%");
    }
}
