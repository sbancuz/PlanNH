package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;

import codechicken.nei.recipe.IRecipeHandler;
import tonius.neiintegration.mods.harvestcraft.RecipeHandlerApiary;
import tonius.neiintegration.mods.harvestcraft.RecipeHandlerChurn;
import tonius.neiintegration.mods.harvestcraft.RecipeHandlerOven;
import tonius.neiintegration.mods.harvestcraft.RecipeHandlerPresser;
import tonius.neiintegration.mods.harvestcraft.RecipeHandlerPresserOld;
import tonius.neiintegration.mods.harvestcraft.RecipeHandlerQuern;

// TODO: Make a PR to expose these constants in the GTNH HarvestCraft fork
// Source: com.pam.harvestcraft.TileEntityPamPresser (combPresserCookTime hardcoded to 125)
// Source: com.pam.harvestcraft.TileEntityChurn (churnCookTime hardcoded to 200)
// Source: com.pam.harvestcraft.TileEntityOven (ovenCookTime hardcoded to 200)
// Source: com.pam.harvestcraft.TileEntityQuern (quernCookTime hardcoded to 200)
// Source: com.pam.harvestcraft.TileEntityPamApiary (getRunTime() returns 3500 base)
public class HarvestCraftProvider implements PropertyProvider {

    private static final String PROFILE_ID = "harvestcraft:basic";

    private static final int PRESSER_TICKS = 125;
    private static final int OVEN_TICKS = 200;
    private static final int CHURN_TICKS = 200;
    private static final int QUERN_TICKS = 200;
    private static final int APIARY_TICKS = 3500;

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(RecipeHandlerPresser.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerPresserOld.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerOven.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerChurn.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerQuern.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerApiary.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder(PROFILE_ID, "HarvestCraft")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(
                    Effects.clearCost()
                        .applyParallelism())
                .build());
    }

    @Override
    public boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        return getProfileId(handler, recipeIndex) != null;
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return switch (handler) {
            case RecipeHandlerPresser _, RecipeHandlerPresserOld _, RecipeHandlerOven _,
                 RecipeHandlerChurn _, RecipeHandlerQuern _, RecipeHandlerApiary _ -> PROFILE_ID;
            default -> null;
        };
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>(
            PropertyProvider.super.extract(node, handler, recipeIndex));

        switch (handler) {
            case RecipeHandlerPresser _, RecipeHandlerPresserOld _ -> props.put(RecipePropertyAPI.DURATION_TICKS, PRESSER_TICKS);
            case RecipeHandlerOven _ -> props.put(RecipePropertyAPI.DURATION_TICKS, OVEN_TICKS);
            case RecipeHandlerChurn _ -> props.put(RecipePropertyAPI.DURATION_TICKS, CHURN_TICKS);
            case RecipeHandlerQuern _ -> props.put(RecipePropertyAPI.DURATION_TICKS, QUERN_TICKS);
            case RecipeHandlerApiary _ -> props.put(RecipePropertyAPI.DURATION_TICKS, APIARY_TICKS);
            default -> {}
        }

        return props;
    }
}
