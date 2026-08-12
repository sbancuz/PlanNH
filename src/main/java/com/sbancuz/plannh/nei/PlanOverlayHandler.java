package com.sbancuz.plannh.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.properties.PropertyProvider;
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
        for (final PropertyProvider p : RecipePropertyAPI.getExtractors(handler.getClass())) {
            if (p.canCraft(handler, recipeIndex)) return true;
        }
        return false;
    }

    @Override
    public boolean craft(final GuiContainer firstGui, final IRecipeHandler handler, final int recipeIndex,
        final int multiplier) {
        for (final PropertyProvider p : RecipePropertyAPI.getExtractors(handler.getClass())) {
            if (p.canCraft(handler, recipeIndex)) return true;
        }
        return false;
    }

    private static void addRecipe(final GuiContainer firstGui, final IRecipeHandler handler, final int recipeIndex) {
        if (firstGui instanceof final GuiContainerWrapper wrapper
            && wrapper.getScreen() instanceof final FlowchartScreen screen) {
            CanvasWidget canvas =  screen.getCanvas();
            canvas.addNode(canvas.getCanvasScreenCenterX(), canvas.getCanvasScreenCenterY(), handler, recipeIndex);

            // todo add to addNode():
            /*PlanAPI.recordEdit(graph, () -> {
                graph.addNode(node);
                if (screen != null) {
                    autoConnectToLookupOrigin(
                        graph,
                        node,
                        screen.canvas.consumePendingLookup(),
                        screen.canvas);
                }
            });*/
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
        final Node origin = graph.getNodes()
            .get(lookupOrigin.nodeId());
        if (origin == null || origin.getId()
            .equals(added.getId())) return;
        final List<Port<?>> originPorts = lookupOrigin.output() ? origin.getOutputs() : origin.getInputs();
        final int originIdx = lookupOrigin.portIndex();
        if (originIdx < 0 || originIdx >= originPorts.size()) return;

        if (lookupOrigin.output()) {
            final int in = graph.findCompatibleInput(origin, originIdx, added);
            if (in >= 0) {
                graph.addEdge(new Edge(UUID.randomUUID(), origin.getId(), added.getId(), originIdx, in));
                canvas.placeBesideOrigin(added, origin, false);
            }
        } else {
            final Port<?> originPort = originPorts.get(originIdx);
            for (int out = 0; out < added.getOutputs()
                .size(); out++) {
                if (!added.getOutputs()
                    .get(out)
                    .canConnect(originPort)) continue;
                graph.addEdge(new Edge(UUID.randomUUID(), added.getId(), origin.getId(), out, originIdx));
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
