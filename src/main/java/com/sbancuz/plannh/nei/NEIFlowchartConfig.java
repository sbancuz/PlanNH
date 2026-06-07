package com.sbancuz.plannh.nei;

import net.minecraftforge.common.MinecraftForge;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.Tags;
import com.sbancuz.plannh.gui.FlowchartGuiContainer;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.GuiRecipeButton.UpdateRecipeButtonsEvent;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeInfo;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class NEIFlowchartConfig implements IConfigureNEI {

    private static final FlowchartOverlayHandler HANDLER = new FlowchartOverlayHandler();

    @Override
    public void loadConfig() {
        Compat.init();
        API.registerNEIGuiHandler(new FlowchartGuiHandler());
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPreButtonUpdate(UpdateRecipeButtonsEvent.Pre event) {
        GuiRecipe<?> gui = (GuiRecipe<?>) event.gui;
        if (!(gui.firstGui instanceof FlowchartGuiContainer)) return;

        for (Object h : gui.currenthandlers) {
            if (!(h instanceof IRecipeHandler r)) continue;
            String ident = r.getOverlayIdentifier();
            if (ident != null && !ident.isEmpty()
                && !RecipeInfo.hasOverlayHandler(FlowchartGuiContainer.class, ident)) {
                API.registerGuiOverlayHandler(FlowchartGuiContainer.class, HANDLER, ident);
            }
        }

        for (GuiRecipeButton btn : event.buttonList) {
            if (btn instanceof GuiOverlayButton) {
                ((GuiOverlayButton) btn).setRequireShiftForOverlayRecipe(false);
            }
        }
    }

    @Override
    public String getName() {
        return "NEI Flowchart";
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }
}
