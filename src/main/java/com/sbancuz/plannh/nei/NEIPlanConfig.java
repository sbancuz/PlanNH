package com.sbancuz.plannh.nei;

import net.minecraftforge.common.MinecraftForge;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.Tags;
import com.sbancuz.plannh.gui.FlowchartScreen;

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
        API.addLayoutStyle(0, new FlowchartLayoutStyle());
        MinecraftForge.EVENT_BUS.register(this);
        API.addOption(new OptionTextField("plannh.itemColumns"));
        ITEM_COLUMNS = NEIClientConfig.getSetting("plannh.itemColumns")
            .setComment("Number of item columns in node widgets");
    }

    @SubscribeEvent
    public void onPreButtonUpdate(final UpdateRecipeButtonsEvent.Pre event) {
        final GuiRecipe<?> gui = (GuiRecipe<?>) event.gui;
        if (!(gui.firstGui instanceof final GuiContainerWrapper wrapper
            && wrapper.getScreen() instanceof FlowchartScreen)) return;

        for (final Object h : gui.currenthandlers) {
            if (!(h instanceof final IRecipeHandler r)) continue;
            final String ident = r.getOverlayIdentifier();
            if (ident != null && !ident.isEmpty()
                && !RecipeInfo.hasOverlayHandler(GuiContainerWrapper.class, ident)) {
                API.registerGuiOverlayHandler(GuiContainerWrapper.class, HANDLER, ident);
            }
        }
    }

    @SubscribeEvent
    public void onPostButtonUpdate(final UpdateRecipeButtonsEvent.Post event) {
        final GuiRecipe<?> gui = (GuiRecipe<?>) event.gui;
        if (!(gui.firstGui instanceof GuiContainerWrapper
            && ((GuiContainerWrapper) gui.firstGui).getScreen() instanceof FlowchartScreen)) return;

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
