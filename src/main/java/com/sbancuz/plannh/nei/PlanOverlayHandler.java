package com.sbancuz.plannh.nei;

import java.util.ArrayList;
import java.util.List;

import com.sbancuz.plannh.gui.CanvasWidget;
import net.minecraft.client.gui.inventory.GuiContainer;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.PropertyProvider;
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

    private static void addRecipe(final GuiContainer firstGui, final IRecipeHandler handler, final int recipeIndex) {
        if (firstGui instanceof final GuiContainerWrapper wrapper
            && wrapper.getScreen() instanceof final FlowchartScreen screen) {
            CanvasWidget canvas =  screen.getCanvas();
            canvas.addNode(canvas.getCanvasScreenCenterX(), canvas.getCanvasScreenCenterY(), handler, recipeIndex);
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
