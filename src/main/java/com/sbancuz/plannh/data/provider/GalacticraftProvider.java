package com.sbancuz.plannh.data.provider;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.effect.steps.CoFHCompat;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;

import codechicken.nei.recipe.IRecipeHandler;
import micdoodle8.mods.galacticraft.core.nei.CircuitFabricatorRecipeHandler;
import micdoodle8.mods.galacticraft.core.nei.RefineryRecipeHandler;

public final class GalacticraftProvider implements PropertyProvider {

    private static final String PROFILE_ID = "gc:basic";

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(RefineryRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(CircuitFabricatorRecipeHandler.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder(PROFILE_ID, "Galacticraft")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(
                    Effects.durationFromHandler()
                        .amortizeCost(CoFHCompat.RF_COST)
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
            case RefineryRecipeHandler _, CircuitFabricatorRecipeHandler _ -> PROFILE_ID;
            default -> null;
        };
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        return PropertyProvider.super.extract(node, handler, recipeIndex);
    }
}
