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

import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.ShapedRecipeHandler;

public class VanillaProvider implements PropertyProvider {

    // Vanilla furnace: base cook time of 200 ticks (10 seconds)
    // Matches net.minecraft.tileentity.TileEntityFurnace.furnaceCookTime
    private static final int FURNACE_COOK_TICKS = 200;

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(FurnaceRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(ShapedRecipeHandler.class, this);
        MachineProfileRegistry.register(
            MachineProfile.builder("minecraft", "Default")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(
                    Effects.durationFromHandler()
                        .applyParallelism())
                .build());
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return switch (handler) {
            case FurnaceRecipeHandler _ -> MachineProfileRegistry.defaultId();
            default -> null;
        };
    }

    @Override
    public boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        return handler instanceof FurnaceRecipeHandler fh && "crafting.furnace".equals(fh.getOverlayIdentifier());
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>(
            PropertyProvider.super.extract(node, handler, recipeIndex));
        if (!(handler instanceof FurnaceRecipeHandler)) return props;
        props.put(RecipePropertyAPI.DURATION_TICKS, FURNACE_COOK_TICKS);
        return props;
    }
}
