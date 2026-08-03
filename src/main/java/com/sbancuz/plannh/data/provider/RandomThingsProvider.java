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
import lumien.randomthings.Handler.ModCompability.NEI.ImbuingStationRecipeHandler;

public final class RandomThingsProvider implements PropertyProvider {

    // TODO: Make a PR to expose this constant
    // Imbuing Station: base process length (ticks) from TileEntityImbuingStation
    private static final int IMBUING_LENGTH = 200;

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(ImbuingStationRecipeHandler.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder("randomthings:imbuing_station", "Imbuing Station")
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
            case ImbuingStationRecipeHandler _ -> "randomthings:imbuing_station";
            default -> null;
        };
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>(
            PropertyProvider.super.extract(node, handler, recipeIndex));
        props.put(RecipePropertyAPI.DURATION_TICKS, IMBUING_LENGTH);
        return props;
    }
}
