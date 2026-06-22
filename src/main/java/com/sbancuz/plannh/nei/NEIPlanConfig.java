package com.sbancuz.plannh.nei;

import net.minecraftforge.common.MinecraftForge;

import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.Tags;
import com.sbancuz.plannh.gui.PlanGuiContainer;

import codechicken.lib.config.ConfigTag;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.config.OptionTextField;
import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.GuiRecipeButton.UpdateRecipeButtonsEvent;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeInfo;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class NEIPlanConfig implements IConfigureNEI {

    public static ConfigTag ITEM_COLUMNS;

    private static final PlanOverlayHandler HANDLER = new PlanOverlayHandler();

    @Override
    public void loadConfig() {
        API.registerNEIGuiHandler(new FlowchartGuiHandler());
        MinecraftForge.EVENT_BUS.register(this);
        API.addOption(new OptionTextField("plannh.itemColumns"));
        ITEM_COLUMNS = NEIClientConfig.getSetting("plannh.itemColumns")
            .setComment("Number of item columns in node widgets");
    }

    @SubscribeEvent
    public void onPreButtonUpdate(final UpdateRecipeButtonsEvent.Pre event) {
        final GuiRecipe<?> gui = (GuiRecipe<?>) event.gui;
        if (!(gui.firstGui instanceof PlanGuiContainer)) return;

        for (final Object h : gui.currenthandlers) {
            if (!(h instanceof final IRecipeHandler r)) continue;
            final String ident = r.getOverlayIdentifier();
            if (ident != null && !ident.isEmpty()
                && !RecipeInfo.hasOverlayHandler(PlanGuiContainer.class, ident)) {
                API.registerGuiOverlayHandler(PlanGuiContainer.class, HANDLER, ident);
            }
        }
    }

    @SubscribeEvent
    public void onPostButtonUpdate(final UpdateRecipeButtonsEvent.Post event) {
        final GuiRecipe<?> gui = (GuiRecipe<?>) event.gui;
        if (!(gui.firstGui instanceof PlanGuiContainer)) return;

        for (final GuiRecipeButton btn : event.buttonList) {
            if (btn instanceof GuiOverlayButton) {
                ((GuiOverlayButton) btn).setRequireShiftForOverlayRecipe(false);
            }
        }
    }

    @Override
    public String getName() {
        return PlanNH.MODID;
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }
}
