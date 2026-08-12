package com.sbancuz.plannh.data.provider;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.setting.Settings;

import codechicken.nei.recipe.IRecipeHandler;
import gcewing.projectblue.nei.NEIRecipeHandler;

public final class ProjectBlueProvider implements PropertyProvider {

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(NEIRecipeHandler.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder("projectblue:basic", "Project Blue")
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
            case NEIRecipeHandler _ -> "projectblue:basic";
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
