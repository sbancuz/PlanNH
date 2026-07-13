package com.sbancuz.plannh.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.gui.FlowchartScreen;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.IRecipeHandler;
import gregtech.api.util.GTUtility;

public class PlanOverlayHandler implements IOverlayHandler {

    @Override
    public void overlayRecipe(final GuiContainer firstGui, final IRecipeHandler recipe, final int recipeIndex,
        final boolean maxTransfer) {
        addRecipe(firstGui, recipe, recipeIndex);
    }

    @Override
    public int transferRecipe(final GuiContainer firstGui, final IRecipeHandler recipe, final int recipeIndex,
        final int multiplier) {
        addRecipe(firstGui, recipe, recipeIndex);
        return 0;
    }

    @Override
    public boolean canCraft(final GuiContainer firstGui, final IRecipeHandler handler, final int recipeIndex) {
        final PropertyProvider provider = RecipePropertyAPI.getExtractor(handler.getOverlayIdentifier());
        if (provider == null) return false;
        return provider.canCraft(handler, recipeIndex);
    }

    @Override
    public boolean craft(final GuiContainer firstGui, final IRecipeHandler handler, final int recipeIndex,
        final int multiplier) {
        final PropertyProvider provider = RecipePropertyAPI.getExtractor(handler.getOverlayIdentifier());
        if (provider == null) return false;
        return provider.canCraft(handler, recipeIndex);
    }

    private static final int DEFAULT_NODE_X = 200;
    private static final int DEFAULT_NODE_Y = 200;

    private static void addRecipe(final GuiContainer firstGui, final IRecipeHandler handler, final int recipeIndex) {
        final Node node = new Node(handler, recipeIndex, DEFAULT_NODE_X, DEFAULT_NODE_Y);
        final Graph graph = PlanAPI.getActiveGraph();
        graph.addNode(node);
        autoConnectToLookupOrigin(graph, node);
        PlanAPI.save();

        if (firstGui instanceof final GuiContainerWrapper wrapper
            && wrapper.getScreen() instanceof final FlowchartScreen screen) {
            screen.canvas.rebuildNodeWidgets();
        }
    }

    /**
     * If this recipe was added from an NEI lookup started on a node's ingredient (R/U on a
     * PlanNH node), wire the new node to that origin - but only on the looked-up ingredient, so
     * lookups that wandered several recipe layers deep don't create bogus edges.
     */
    private static void autoConnectToLookupOrigin(final Graph graph, final Node added) {
        final UUID originId = NodeLookupContext.nodeId();
        final ItemStack lookup = NodeLookupContext.stack();
        if (originId == null || lookup == null) return;
        final Node origin = graph.nodes.get(originId);
        if (origin == null || origin.id.equals(added.id)) return;

        // R lookup: the new recipe produces what the origin consumes...
        if (connectOnIngredient(graph, added, origin, lookup)) return;
        // ...or U lookup: the new recipe consumes what the origin produces.
        connectOnIngredient(graph, origin, added, lookup);
    }

    /** Wires src's lookup-matching output to dst's first compatible (preferably unfed) input. */
    private static boolean connectOnIngredient(final Graph graph, final Node src, final Node dst,
        final ItemStack lookup) {
        for (int out = 0; out < src.outputs.size(); out++) {
            final Port<?> outPort = src.outputs.get(out);
            if (!portMatchesLookup(outPort, lookup)) continue;
            int chosen = -1;
            for (int in = 0; in < dst.inputs.size(); in++) {
                if (!outPort.canConnect(dst.inputs.get(in))) continue;
                if (chosen < 0) chosen = in;
                if (!hasIncomingEdge(graph, dst.id, in)) {
                    chosen = in;
                    break;
                }
            }
            if (chosen >= 0) {
                graph.addEdge(new Edge(UUID.randomUUID(), src.id, dst.id, out, chosen));
                return true;
            }
        }
        return false;
    }

    private static boolean portMatchesLookup(final Port<?> port, final ItemStack lookup) {
        final Object value = port.getValue();
        if (value instanceof final ItemStack stack) {
            return stack.isItemEqual(lookup);
        }
        if (value instanceof final FluidStack fluidValue && ModularUI.Mods.GT5U.isLoaded()) {
            final FluidStack lookupFluid = GTUtility.getFluidFromDisplayStack(lookup);
            return lookupFluid != null && lookupFluid.getFluid() == fluidValue.getFluid();
        }
        return false;
    }

    private static boolean hasIncomingEdge(final Graph graph, final UUID nodeId, final int inputIndex) {
        for (final Edge edge : graph.getEdges()) {
            if (edge.targetNodeId.equals(nodeId) && edge.targetInputIndex == inputIndex) return true;
        }
        return false;
    }

    @Override
    public List<GuiOverlayButton.ItemOverlayState> presenceOverlay(final GuiContainer firstGui,
        final IRecipeHandler recipe, final int recipeIndex) {
        final List<GuiOverlayButton.ItemOverlayState> itemPresenceSlots = new ArrayList<>();
        final List<PositionedStack> ingredients = recipe.getIngredientStacks(recipeIndex);

        for (final PositionedStack stack : ingredients) {
            itemPresenceSlots.add(new GuiOverlayButton.ItemOverlayState(stack, true));
        }

        return itemPresenceSlots;
    }
}
