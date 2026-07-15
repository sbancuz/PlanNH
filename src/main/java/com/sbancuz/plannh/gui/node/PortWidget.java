package com.sbancuz.plannh.gui.node;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.flowchart.Port;

import codechicken.nei.recipe.RecipeHandlerRef;
import it.unimi.dsi.fastutil.Pair;

public class PortWidget extends Widget<PortWidget> {

    private static final int INPUT_COLOR = Color.GREEN.main;
    private static final int OUTPUT_COLOR = Color.BLUE.main;
    // needed for proper positioning of ports
    private static final int PORT_OFFSET_X = 1;
    private static final int PORT_OFFSET_Y = -1;

    private final RecipeHandlerRef handlerRef;
    // this specifies highlight color and dragging behaviour (start/end of arrow)
    private final boolean isInput;

    public PortWidget(RecipeHandlerRef handlerRef, boolean isInput, Pair<Integer, Integer> pos, Port<?> port) {
        this.handlerRef = handlerRef;
        this.isInput = isInput;

        background(
            new Rectangle().color(isInput ? INPUT_COLOR : OUTPUT_COLOR)
                .hollow());
        hoverOverlay(new Rectangle().color(Color.argb(255, 255, 255, 128)));
        pos(pos.first() + PORT_OFFSET_X, pos.second() + PORT_OFFSET_Y);

        if (port.getType() == RecipePropertyAPI.ITEM) tooltip(t -> t.addFromItem((ItemStack) port.getValue()));
        if (port.getType() == RecipePropertyAPI.FLUID) tooltip(t -> t.addFromFluid((FluidStack) port.getValue()));
        addTooltipLine(port.getChance() * 100 + "%");
    }
}
