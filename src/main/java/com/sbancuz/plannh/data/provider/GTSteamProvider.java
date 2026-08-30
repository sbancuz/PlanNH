package com.sbancuz.plannh.data.provider;

import java.util.Map;
import java.util.Set;

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
import com.sbancuz.plannh.data.properties.SummaryProperty;
import com.sbancuz.plannh.data.provider.gregtech.GTSteamOverclockStep;

import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.IRecipeHandler;
import gregtech.api.recipe.RecipeMap;
import gregtech.nei.GTNEIDefaultHandler;

public class GTSteamProvider implements PropertyProvider {

    public static final RecipeProperty<Long> STEAM_EU_PERT = SummaryProperty.<Long>builder("gt.steam_eu_pert", 0L)
        .perSec(true)
        .build();
    public static final RecipeProperty<Long> TOTAL_STEAM_EU = SummaryProperty.<Long>builder("gt.total_steam_eu", 0L)
        .build();

    private static final Set<String> STEAM_RECIPE_MAPS = Set.of(
        "gt.recipe.furnace",
        "gt.recipe.efrblasting",
        "gt.recipe.efrsmelting",
        "gt.recipe.macerator",
        "gt.recipe.compressor",
        "gt.recipe.hammer",
        "gt.recipe.alloysmelter",
        "gt.recipe.centrifuge",
        "gt.recipe.orewasher",
        "gtpp.recipe.multimixer",
        "gtpp.recipe.simplewasher");

    private static final MachineProfile STEAM_PROFILE = MachineProfile.builder("gregtech:steam", "GT Steam")
        .setting(Settings.SPEED.def())
        .setting(Settings.PARALLELS.def())
        .setting(Settings.MACHINES.def())
        .setting(Settings.STEAM_EUT_DISCOUNT.def())
        .setting(Settings.STEAM_DURATION_MODIFIER.def())
        .effect(
            Effects.durationFromHandler()
                .andThen(GTSteamOverclockStep.create())
                .applyParallelism())
        .build();

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(FurnaceRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(GTNEIDefaultHandler.class, this);
        MachineProfileRegistry.register(STEAM_PROFILE);
    }

    @Override
    public boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        if (handler instanceof FurnaceRecipeHandler) return true;
        if (handler instanceof final GTNEIDefaultHandler gth) {
            final RecipeMap<?> map = gth.getRecipeMap();
            return map != null && STEAM_RECIPE_MAPS.contains(map.unlocalizedName);
        }
        return false;
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        if (handler instanceof FurnaceRecipeHandler) return STEAM_PROFILE.id();
        if (handler instanceof GTNEIDefaultHandler) return STEAM_PROFILE.id();
        return null;
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        return GTProvider.extractGTRecipe(node, handler, recipeIndex);
    }
}
