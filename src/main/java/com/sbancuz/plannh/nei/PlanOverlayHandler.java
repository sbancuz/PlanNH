package com.sbancuz.plannh.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
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
        PlanAPI.save();
        if (screen != null) {
            screen.canvas.rebuildNodeWidgets();
        }
    }

    /**
     * If this recipe was added from an NEI lookup started on a node's ingredient (R/U on a
     * PlanNH node), wire the new node to that origin - but only on the looked-up ingredient, so
     * lookups that wandered several recipe layers deep don't create bogus edges.
     *
     * <p>
     * The lookup DIRECTION follows which side of the added recipe carries the ingredient: a
     * producer (what R lookups list) feeds the origin, a consumer (U lookups) is fed by it. The
     * NEI screen type cannot decide this - GuiOverlayButton switches back to the flowchart
     * before invoking the overlay handler, so the recipe screen is already gone here - and after
     * wandering several layers deep it would not reflect the original R/U anyway.
     *
     * <p>
     * A wired node is also placed beside its origin (producers left, consumers right) instead of
     * at the default spawn position.
     */
    private static void autoConnectToLookupOrigin(final Graph graph, final Node added,
        @Nullable final NodeLookupContext lookupOrigin, final CanvasWidget canvas) {
        if (lookupOrigin == null) return;
        final Node origin = graph.nodes.get(lookupOrigin.nodeId());
        final ItemStack lookup = lookupOrigin.stack();
        if (origin == null || lookup == null || origin.id.equals(added.id)) return;

        if (connectOnIngredient(graph, added, origin, lookup)) {
            canvas.placeBesideOrigin(added, origin, true);
        } else if (connectOnIngredient(graph, origin, added, lookup)) {
            canvas.placeBesideOrigin(added, origin, false);
        }
    }

    /** Wires src's lookup-matching output to dst's first compatible (preferably unfed) input. */
    private static boolean connectOnIngredient(final Graph graph, final Node src, final Node dst,
        final ItemStack lookup) {
        for (int out = 0; out < src.outputs.size(); out++) {
            if (!src.outputs.get(out)
                .matchesLookup(lookup)) continue;
            final int in = graph.findCompatibleInput(src, out, dst);
            if (in >= 0) {
                graph.addEdge(new Edge(UUID.randomUUID(), src.id, dst.id, out, in));
                return true;
            }
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
