package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
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
import flaxbeard.thaumicexploration.integration.nei.ReplicatorHandler;

public final class ThaumicExplorationProvider implements PropertyProvider {

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(ReplicatorHandler.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder("tx:replicator", "Thaumic Replicator")
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

    // TODO: Make a PR to expose this constant
    // Thaumic Replicator: base countdown time (ticks) from TileReplicator
    private static final int REPLICATOR_COUNTDOWN = 100;

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return switch (handler) {
            case ReplicatorHandler _ -> "tx:replicator";
            default -> null;
        };
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>(
            PropertyProvider.super.extract(node, handler, recipeIndex));
        props.put(RecipePropertyAPI.DURATION_TICKS, REPLICATOR_COUNTDOWN);
        return props;
    }
}
