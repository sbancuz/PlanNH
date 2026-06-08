package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.Map;

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
    public String getModId() {
        return "minecraft";
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        MachineProfileRegistry.register(
            MachineProfile.builder("minecraft", "Default")
                .setting(Settings.MACHINES.def())
                .effect(VanillaProvider::vanillaEffect)
                .build());
    }

    private static MachineProfile.EffectResult vanillaEffect(Map<String, Object> s, MachineProfile.RecipeContext ctx) {
        int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        return new MachineProfile.EffectResult(ctx.recipeDuration(), ctx.recipeEUt(), machines);
    }

    @Override
    public String getProfileId(IRecipeHandler handler, int recipeIndex) {
        if (!(handler instanceof FurnaceRecipeHandler)) return null;
        return MachineProfileRegistry.defaultId();
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(Node node, IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof FurnaceRecipeHandler)) return props;
        props.put(RecipePropertyAPI.DURATION_TICKS, 200);
        return props;
    }
}
