package com.sbancuz.plannh.nei;

import net.minecraftforge.common.MinecraftForge;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.Tags;
import com.sbancuz.plannh.gui.FlowchartScreen;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.config.OptionCycled;
import codechicken.nei.config.OptionIntegerField;
import codechicken.nei.config.OptionTextField;
import codechicken.nei.recipe.GuiOverlayButton;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.GuiRecipeButton.UpdateRecipeButtonsEvent;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeInfo;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class NEIPlanConfig implements IConfigureNEI {

    public static class ConfigItemColumns {

        public static String KEY = "plannh.item_columns";
        public static int min = 6;
        public static int max = Integer.MAX_VALUE;
        public static int defVal = 9;
    }

    public static class ConfigBurnableOverride {

        public static String KEY = "plannh.burnable_override";
        public static int OFF = 0;
        public static int ON = 1;
    }

    public static class ConfigBlurStrength {

        public static String KEY = "plannh.blur_strength";
        public static int min = 0;
        public static int max = 255;
        public static int defVal = 16;
    }

    public static class ConfigShowGrid {

        public static String KEY = "plannh.show_grid";
        public static int OFF = 0;
        public static int ON = 1;
    }

    public static class ConfigBackgroundColor {

        public static String KEY = "plannh.background_color";
    }

    private static final PlanOverlayHandler HANDLER = new PlanOverlayHandler();

    @Override
    public void loadConfig() {
        API.registerNEIGuiHandler(new FlowchartGuiHandler());
        API.addLayoutStyle(0, new FlowchartLayoutStyle());
        MinecraftForge.EVENT_BUS.register(this);
        API.addOption(new OptionCycled(ConfigBurnableOverride.KEY, 2) {

            public boolean onClick(int button) {
                if (!super.onClick(button)) {
                    return false;
                } else {
                    Compat.init();
                    return true;
                }
            }
        });

        API.addOption(new OptionIntegerField(ConfigItemColumns.KEY, ConfigItemColumns.min, ConfigItemColumns.max));
        API.addOption(new OptionIntegerField(ConfigBlurStrength.KEY, ConfigBlurStrength.min, ConfigBlurStrength.max));
        API.addOption(new OptionCycled(ConfigShowGrid.KEY, 2));
        API.addOption(new OptionTextField(ConfigBackgroundColor.KEY));
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
