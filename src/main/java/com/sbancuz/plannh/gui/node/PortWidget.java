package com.sbancuz.plannh.gui.node;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.gui.CanvasWidget;

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

    private final CanvasWidget canvas;

    // save, so we can toggle permutation
    private final PositionedStack stack;
    // this specifies highlight color and dragging behaviour (start/end of arrow)
    private final PortType portType;
    private final Port<?> port;

    public PortWidget(CanvasWidget canvas, Port<?> port, PortType portType, int yShift, IntIntPair pos) {
        this.canvas = canvas;
        this.stack = port.getStack();
        this.portType = portType;
        this.port = port;

        background(
            new Rectangle().color(portType.borderColor)
                .hollow());
        hoverOverlay(new Rectangle().color(Color.argb(255, 255, 255, 128)));
        pos(pos.leftInt() + PORT_OFFSET_X, pos.rightInt() + PORT_OFFSET_Y + yShift);

        tooltipBuilder(t -> {
            t.addFromItem(stack.item);
            t.addLine(String.format("%.2f", port.getChance() * 100) + "%");
            if (stack.items.length > 1) t.addLine("Right-Click to Configure");
        });
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

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (mouseButton == 1) {
            // todo add config menu
        }
        return Result.SUCCESS;
    }

    @Override
    public boolean onMouseRelease(int mouseButton) {
        // if addingArrow & compatible, add arrow; else remove arrow

        return Interactable.super.onMouseRelease(mouseButton);
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        if (mouseButton == 0 && portType.supportsEdge && !canvas.isCreatingEdge()) return; // initiate arrow creation
    }

    public enum PortType {

        INPUT(Color.GREEN.main, true, false),
        OUTPUT(Color.BLUE.main, true, true),
        CATALYST(Color.BLACK.main, false, false);

        private final int borderColor;
        private final boolean supportsEdge;
        private final boolean edgeOrigin;

        PortType(int borderColor, boolean supportsEdge, boolean edgeOrigin) {
            this.borderColor = borderColor;
            this.supportsEdge = supportsEdge;
            this.edgeOrigin = edgeOrigin;
        }
    }
}
