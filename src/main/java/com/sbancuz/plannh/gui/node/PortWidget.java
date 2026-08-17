package com.sbancuz.plannh.gui.node;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.data.flowchart.Port;

import codechicken.nei.KeyManager;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import it.unimi.dsi.fastutil.ints.IntIntPair;

public class PortWidget extends Widget<PortWidget> implements Interactable {

    private static final int RECIPE_KEYCODE = KeyManager.getKeyCode("recipe.recipe");
    private static final int USAGE_KEYCODE = KeyManager.getKeyCode("recipe.usage");

    // needed for proper positioning of ports
    private static final int PORT_OFFSET_X = 1;
    private static final int PORT_OFFSET_Y = -1;

    // save, so we can toggle permutation
    private final PositionedStack stack;
    // this specifies highlight color and dragging behaviour (start/end of arrow)
    private final PortType portType;
    private final Port<?> port;

    public PortWidget(Port<?> port, PortType portType, int yShift, IntIntPair pos) {
        this.stack = port.getStack();
        this.portType = portType;
        this.port = port; // todo needed?

        background(
            new Rectangle().color(portType.borderColor)
                .hollow());
        hoverOverlay(new Rectangle().color(Color.argb(255, 255, 255, 128)));
        pos(pos.leftInt() + PORT_OFFSET_X, pos.rightInt() + PORT_OFFSET_Y + yShift);

        tooltipBuilder(
            t -> t.addFromItem(stack.item)
                .addLine(String.format("%.2f", port.getChance() * 100) + "%"));
        tooltipAutoUpdate(true);
    }

    @Override
    public @NotNull Result onKeyPressed(char typedChar, int keyCode) {
        if (keyCode == RECIPE_KEYCODE) {
            GuiCraftingRecipe.openRecipeGui("item", stack.item);
            return Result.ACCEPT;
        }
        if (keyCode == USAGE_KEYCODE) {
            GuiUsageRecipe.openRecipeGui("item", stack.item);
            return Result.ACCEPT;
        }
        return Interactable.super.onKeyPressed(typedChar, keyCode);
    }

    public enum PortType {

        INPUT(Color.GREEN.main),
        OUTPUT(Color.BLUE.main),
        CATALYST(Color.BLACK.main);

        private final int borderColor;

        PortType(int borderColor) {
            this.borderColor = borderColor;
        }
    }
}
