package com.sbancuz.plannh.nei;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiOverlayButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ChatComponentText;

import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.gui.FlowchartGuiContainer;
import com.sbancuz.plannh.gui.FlowchartScreen;

import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;

import java.util.ArrayList;
import java.util.List;

public class FlowchartOverlayHandler implements IOverlayHandler {

    @Override
    public void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer) {
        addRecipe(firstGui, recipe, recipeIndex);
    }

    @Override
    public int transferRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, int multiplier) {
        addRecipe(firstGui, recipe, recipeIndex);
        return 0;
    }

    @Override
    public boolean canCraft(GuiContainer firstGui, IRecipeHandler handler, int recipeIndex) {
        for (PropertyProvider provider : RecipePropertyAPI.getExtractors()) {
            if (!provider.canCraft(handler, recipeIndex)) return false;
        }

        return true;
    }

    @Override
    public boolean canFillCraftingGrid(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex) {
        for (PropertyProvider provider : RecipePropertyAPI.getExtractors()) {
            if (!provider.canCraft(recipe, recipeIndex)) return false;
        }

        return true;
    }

    @Override
    public boolean craft(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, int multiplier) {
        for (PropertyProvider provider : RecipePropertyAPI.getExtractors()) {
            if (!provider.canCraft(recipe, recipeIndex)) return false;
        }

        return true;
    }

    private static void addRecipe(GuiContainer firstGui, IRecipeHandler handler, int recipeIndex) {
        for (PropertyProvider provider : RecipePropertyAPI.getExtractors()) {
            if (!provider.canCraft(handler, recipeIndex)) return;
        }
        Node node = new Node(handler, recipeIndex, 200, 200);
        Graph graph = PlanAPI.getActiveGraph();
        graph.addNode(node);
        PlanAPI.save();

        if (firstGui instanceof FlowchartGuiContainer) {
            Object screen = ((FlowchartGuiContainer) firstGui).getScreen();
            if (screen instanceof FlowchartScreen) {
                ((FlowchartScreen) screen).canvas.rebuildNodeWidgets();
            }
        }
    }

    @Override
    public List<GuiOverlayButton.ItemOverlayState> presenceOverlay(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex) {
        final List<GuiOverlayButton.ItemOverlayState> itemPresenceSlots = new ArrayList<>();
        final List<PositionedStack> ingredients = recipe.getIngredientStacks(recipeIndex);

        for (PositionedStack stack : ingredients) {
            itemPresenceSlots.add(new GuiOverlayButton.ItemOverlayState(stack, true));
        }

        return itemPresenceSlots;
    }
}
