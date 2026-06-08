package com.sbancuz.plannh.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
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

}
