package com.sbancuz.plannh.data.provider;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.brandon3055.draconicevolution.integration.nei.ReactorNEIHandler;
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

public final class DraconicEvolutionProvider implements PropertyProvider {

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(ReactorNEIHandler.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder("draconicevolution:fusion_crafter", "Fusion Crafter")
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
            case ReactorNEIHandler _ -> "draconicevolution:fusion_crafter";
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
