package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.flowchart.Node;

import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.IRecipeHandler;

public class VanillaProvider implements PropertyProvider {

    @Override
    @Nonnull
    public String getModId() {
        return "minecraft";
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(new FurnaceRecipeHandler().getOverlayIdentifier(), this);
        MachineProfileRegistry.register(
            MachineProfile.builder("minecraft", "Default")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(VanillaProvider::vanillaEffect)
                .build());
    }

    @Nonnull
    private static MachineProfile.EffectResult vanillaEffect(final Map<String, Object> s,
        final MachineProfile.RecipeContext ctx) {
        final int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        return new MachineProfile.EffectResult(ctx.recipeDuration(), ctx.recipeEUt(), machines);
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        if (!(handler instanceof FurnaceRecipeHandler)) return null;
        return MachineProfileRegistry.defaultId();
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof FurnaceRecipeHandler)) return props;
        props.put(RecipePropertyAPI.DURATION_TICKS, 200);
        return props;
    }
}
