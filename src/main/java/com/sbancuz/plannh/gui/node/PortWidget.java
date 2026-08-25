package com.sbancuz.plannh.gui.node;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.sbancuz.plannh.data.flowchart.Edge2;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.edge.ArrowWidget;

import codechicken.nei.KeyManager;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import lombok.Getter;

public class PortWidget extends Widget<PortWidget> implements Interactable, IDraggable {

    private static final int RECIPE_KEYCODE = KeyManager.getKeyCode("recipe.recipe");
    private static final int USAGE_KEYCODE = KeyManager.getKeyCode("recipe.usage");

    // needed for proper positioning of ports
    private static final int PORT_OFFSET_X = 1;
    private static final int PORT_OFFSET_Y = -1;

    private final CanvasWidget canvas;

    private static boolean isCreatingEdge = false;
    public static ArrowWidget arrowWidgetInCreation;

    // saved, so we can toggle permutation
    private final PositionedStack stack;
    @Getter
    private final PortType portType;
    @Getter
    private final Port<?> port;
    private final IntIntPair index;
    private final UUID nodeId;

    public PortWidget(CanvasWidget canvas, Port<?> port, PortType portType, int yShift, IntIntPair index, UUID nodeId) {
        this.canvas = canvas;
        this.stack = port.getAllStacks()
            .get(index.secondInt());
        this.portType = portType;
        this.port = port;
        this.index = index;
        this.nodeId = nodeId;

        background(
            new Rectangle().color(portType.borderColor)
                .hollow());
        hoverOverlay(new Rectangle().color(Color.argb(255, 255, 255, 128)));
        pos(stack.relx + PORT_OFFSET_X, stack.rely + PORT_OFFSET_Y + yShift);

        tooltipBuilder(t -> {
            t.addFromItem(stack.item);
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
            // todo add config menu, mixin to set permutated and item, and one to early return from
            // setPermutationToRender if permutated is false
            return Result.SUCCESS;
        }
        return Result.ACCEPT;
    }

    public boolean canConnect(PortWidget other) {
        return portType.canConnect(other.portType) && port.canConnect(other.port);
    }

    @Override
    public boolean onDragStart(int button) {
        if (button == 0 && portType.supportsEdge) {
            arrowWidgetInCreation = new ArrowWidget(canvas);
            canvas.child(arrowWidgetInCreation);

            return true;
        }
        return false;
    }

    @Override
    public void onDragEnd(boolean successful) {
        if (successful) {
            PortWidget other = (PortWidget) getContext().getTopHovered();
            Edge2 edge;

            if (portType.origin) edge = new Edge2(nodeId, other.nodeId, index, other.index);
            else edge = new Edge2(other.nodeId, nodeId, other.index, index);

            canvas.getGraph()
                .getEdges2()
                .put(edge.getId(), edge);
        } else {
            canvas.remove(arrowWidgetInCreation);
        }
        arrowWidgetInCreation = null;
    }

    @Override
    public void onDrag(int mouseButton, long timeSinceLastClick) {
        Area startArea = getArea();
        Area canvasArea = canvas.getArea();

        int startX = startArea.x + startArea.width / 2 - canvasArea.x;
        int startY = startArea.y + startArea.height / 2 - canvasArea.y;
        int endX = canvas.getCanvasMouseX();
        int endY = canvas.getCanvasMouseY();

        List<int[]> coords = List
            .of(new int[] { startX, startY }, new int[] { endX, startY }, new int[] { endX, endY });

        arrowWidgetInCreation.setCoords(portType.origin ? coords : coords.reversed());
    }

    @Override
    public boolean canDropHere(int x, int y, @Nullable IWidget widget) {
        return widget instanceof PortWidget other && canConnect(other);
    }

    @Override
    public boolean isMoving() {
        return isCreatingEdge;
    }

    @Override
    public void setMoving(boolean moving) {
        isCreatingEdge = moving;
    }

    @Override
    public void drawMovingState(ModularGuiContext context, float partialTicks) {}

    @Override
    public @Nullable Area getMovingArea() {
        return null;
    }

    @Override
    public void drawForeground(ModularGuiContext context) {
        RichTooltip tooltip = getTooltip();
        if (tooltip != null && isHoveringFor(tooltip.getShowUpTimer()) && (!context.hasDraggable() || isCreatingEdge)) {
            tooltip.draw(context);
        }
    }

    @Getter
    public enum PortType {

        INPUT(Color.GREEN.main, true, false),
        OUTPUT(Color.BLUE.main, true, true),
        CATALYST(Color.BLACK.main, false);

        private final int borderColor;
        private final boolean supportsEdge;
        private final boolean origin;

        PortType(int borderColor, boolean supportsEdge, boolean origin) {
            this.borderColor = borderColor;
            this.supportsEdge = supportsEdge;
            this.origin = origin;
        }

        PortType(int borderColor, boolean supportsEdge) {
            this(borderColor, supportsEdge, false);
        }

        private boolean canConnect(PortType other) {
            return switch (this) {
                case CATALYST -> false;
                case INPUT -> other == OUTPUT;
                case OUTPUT -> other == INPUT;
            };
        }
    }
}
