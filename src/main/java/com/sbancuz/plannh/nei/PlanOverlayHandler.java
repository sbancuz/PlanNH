package com.sbancuz.plannh.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.FlowchartScreen;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.IRecipeHandler;

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
        final String before = PlanAPI.undoHistory()
            .beginEdit(graph);
        graph.addNode(node);

        final FlowchartScreen screen = firstGui instanceof final GuiContainerWrapper wrapper
            && wrapper.getScreen() instanceof final FlowchartScreen s ? s : null;
        if (screen != null) {
            autoConnectToLookupOrigin(
                graph,
                node,
                screen.canvas.consumePendingLookup(),
                screen.canvas);
        }
        PlanAPI.undoHistory()
            .commitEdit(before, graph);
        PlanAPI.save();
        if (screen != null) {
            screen.canvas.rebuildNodeWidgets();
        }
    }

    /**
     * If this recipe was added from an NEI lookup started on a node's port, wire the new node
     * to that exact port and place it beside the origin node. Direction follows the port's side: an
     * inputs on the left, outputs on the right.
     */
    private static void autoConnectToLookupOrigin(final Graph graph, final Node added,
        @Nullable final NodeLookupContext lookupOrigin, final CanvasWidget canvas) {
        if (lookupOrigin == null) return;
        final Node origin = graph.nodes.get(lookupOrigin.nodeId());
        if (origin == null || origin.id.equals(added.id)) return;
        final List<Port<?>> originPorts = lookupOrigin.output() ? origin.outputs : origin.inputs;
        final int originIdx = lookupOrigin.portIndex();
        if (originIdx < 0 || originIdx >= originPorts.size()) return;

        if (lookupOrigin.output()) {
            final int in = graph.findCompatibleInput(origin, originIdx, added);
            if (in >= 0) {
                graph.addEdge(new Edge(UUID.randomUUID(), origin.id, added.id, originIdx, in));
                canvas.placeBesideOrigin(added, origin, false);
            }
        } else {
            final Port<?> originPort = originPorts.get(originIdx);
            for (int out = 0; out < added.outputs.size(); out++) {
                if (!added.outputs.get(out)
                    .canConnect(originPort)) continue;
                graph.addEdge(new Edge(UUID.randomUUID(), added.id, origin.id, out, originIdx));
                canvas.placeBesideOrigin(added, origin, true);
                break;
            }
        }
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
