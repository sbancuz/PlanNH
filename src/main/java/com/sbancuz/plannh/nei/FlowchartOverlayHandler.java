package com.sbancuz.plannh.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.FlowchartGraph;
import com.sbancuz.plannh.data.FlowchartNode;
import com.sbancuz.plannh.gui.FlowchartGuiContainer;
import com.sbancuz.plannh.gui.FlowchartScreen;

import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;

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
        return true;
    }

    private static void addRecipe(GuiContainer firstGui, IRecipeHandler handler, int recipeIndex) {
        FlowchartNode node = new FlowchartNode(handler, recipeIndex, 200, 200);
        FlowchartGraph graph = PlanAPI.getGraph(firstGui);
        graph.addNode(node);
        PlanAPI.saveGraph(graph);

        if (firstGui instanceof FlowchartGuiContainer) {
            Object screen = ((FlowchartGuiContainer) firstGui).getScreen();
            if (screen instanceof FlowchartScreen) {
                ((FlowchartScreen) screen).canvas.rebuildNodeWidgets();
            }
        }
    }

}
