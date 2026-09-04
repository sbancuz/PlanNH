package com.sbancuz.plannh.gui.node;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.flowchart.Edge2;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.PlannhColors;
import com.sbancuz.plannh.gui.edge.ArrowWidget;
import com.sbancuz.plannh.mixins.PositionedStackAccessor;

import codechicken.nei.KeyManager;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import gregtech.api.util.GTUtility;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import lombok.Getter;

public class PortWidget extends Widget<PortWidget> implements Interactable, IDraggable {

    private static final int RECIPE_KEYCODE = KeyManager.getKeyCode("recipe.recipe");
    private static final int USAGE_KEYCODE = KeyManager.getKeyCode("recipe.usage");

    // needed for proper positioning of ports
    private static final int PORT_OFFSET_X = 1;
    private static final int PORT_OFFSET_Y = -1;
    private static final int PORT_CONFIG_GRID_WIDTH = 4;
    private static final int PORT_SIZE = 18;

    private final CanvasWidget canvas;

    private static boolean isCreatingEdge = false;
    public static ArrowWidget arrowWidgetInCreation;

    // saved, so we can toggle permutation
    private final PositionedStack stack;
    @Getter
    private final PortType portType;
    @Getter
    private final Port<?> port;
    @Getter
    private final IntIntPair index;
    @Getter
    private final Node node;
    private final boolean isInput;

    private boolean isConfiguring = false;
    private final Grid grid;
    private final RecipeAreaWidget parent;

    public PortWidget(CanvasWidget canvas, RecipeAreaWidget parent, Node node, IntIntPair index, boolean isInput,
        PortType portType, int yShift) {
        this.canvas = canvas;
        this.parent = parent;
        this.node = node;
        this.index = index;
        this.isInput = isInput;
        this.port = (isInput ? node.getInputs() : node.getOutputs()).get(index.firstInt());
        this.stack = port.getAllStacks()
            .get(index.secondInt());
        this.portType = portType;

        background(
            new Rectangle().color(portType.borderColor)
                .hollow());
        hoverOverlay(new Rectangle().color(Color.argb(255, 255, 255, 128)));
        pos(stack.relx + PORT_OFFSET_X, stack.rely + PORT_OFFSET_Y + yShift);
        size(PORT_SIZE);

        tooltipBuilder(t -> {
            t.addFromItem(stack.item);
            if (configurable()) t.addLine("Right-Click to Configure");
        });
        tooltipAutoUpdate(true);

        if (configurable()) {
            List<ItemStack> items = new ArrayList<>(List.of(stack.items));
            items.addFirst(null);

            grid = new Grid().coverChildren()
                .pos(stack.relx + PORT_OFFSET_X + PORT_SIZE, stack.rely + PORT_OFFSET_Y + yShift)
                .setEnabledIf(_ -> isConfiguring)
                .gridOfWidthElements(
                    PORT_CONFIG_GRID_WIDTH,
                    items,
                    (_, _, _, itemStack) -> createConfigButton(itemStack));

            overlay(
                GuiTextures.GEAR.asIcon()
                    .size(9)
                    .alignment(Alignment.TopLeft));
        } else grid = null;
    }

    private ButtonWidget<?> createConfigButton(@Nullable ItemStack itemStack) {
        ButtonWidget<?> button = new ButtonWidget<>().padding(1)
            .coverChildren()
            .background(
                new Rectangle().color(Color.GREY.darker(2)),
                new Rectangle().hollow()
                    .color(PlannhColors.CONTEXT_BORDER.getColor())) // todo this with themes
            .onMousePressed(_ -> {
                PlanAPI.recordEdit(canvas.getGraph(), () -> {
                    if (itemStack != null) setPermutationToStack(itemStack);
                    else enablePermutations();
                });
                isConfiguring = false;
                parent.remove(grid);
                return true;
            });

        if (itemStack == null) button.child(
            GuiTextures.REFRESH.asWidget()
                .size(PORT_SIZE - 2)
                .addTooltipLine("Reset"));
        else button.child(
            new ItemDrawable(itemStack).asWidget()
                .size(PORT_SIZE - 2)
                .tooltipBuilder(t -> t.addFromItem(itemStack)));

        return button;
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
        if (mouseButton == 1 && configurable()) {
            if (!isConfiguring) {
                // child of the parent for proper z-layer positioning
                parent.child(grid);
                isConfiguring = true;
            } else {
                parent.remove(grid);
                isConfiguring = false;
            }
            return Result.SUCCESS;
        }
        return Result.ACCEPT;
    }

    @SuppressWarnings("unchecked")
    private void setPermutationToStack(ItemStack itemStack) {
        if (port.getType() == RecipePropertyAPI.ITEM) ((Port<ItemStack>) port).setValue(itemStack.copy());
        if (port.getType() == RecipePropertyAPI.FLUID && Compat.GREGTECH.isLoaded) ((Port<FluidStack>) port).setValue(
            GTUtility.getFluidFromDisplayStack(itemStack)
                .copy());

        port.getAllStacks()
            .forEach(ps -> {
                PositionedStackAccessor psa = (PositionedStackAccessor) ps;
                psa.setPermutated(true);
                ps.setPermutationToRender(itemStack);
                psa.setPermutated(false);
            });

        node.getInputConfigurations()
            .put(index.firstInt(), itemStack);
    }

    private void enablePermutations() {
        port.getAllStacks()
            .forEach(ps -> {
                PositionedStackAccessor psa = (PositionedStackAccessor) ps;
                psa.setPermutated(true);
            });

        node.getInputConfigurations()
            .remove(index.firstInt());
    }

    private boolean canConnect(PortWidget other) {
        return portType.canConnect(other.portType) && port.canConnect(other.port);
    }

    private boolean configurable() {
        return stack.items.length > 1 && isInput;
    }

    private boolean notConfigured() {
        return ((PositionedStackAccessor) stack).getPermutated();
    }

    @Override
    public boolean onDragStart(int button) {
        if (button == 0 && portType.supportsEdge && !isConfiguring) {
            arrowWidgetInCreation = new ArrowWidget(canvas);
            canvas.child(arrowWidgetInCreation);

            return true;
        }
        return false;
    }

    @Override
    public void onDragEnd(boolean successful) {
        canvas.remove(arrowWidgetInCreation);
        arrowWidgetInCreation = null;

        if (successful) {
            PortWidget source;
            PortWidget target;
            if (portType.origin) {
                source = this;
                target = (PortWidget) getContext().getTopHovered();
            } else {
                source = (PortWidget) getContext().getTopHovered();
                target = this;
            }

            if (source == null || target == null) {
                PlanNH.LOG.warn("Edge creation unsuccessful: ports were null");
                return;
            }

            Graph graph = canvas.getGraph();
            PlanAPI.recordEdit(graph, () -> {
                if (target.notConfigured() && target.configurable()) target.setPermutationToStack(source.stack.item);

                Edge2 edge = new Edge2(source, target);
                graph.getEdges2()
                    .put(edge.getId(), edge);

                ArrowWidget arrow = new ArrowWidget(canvas, edge);
                canvas.child(arrow);
            });
        }
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
