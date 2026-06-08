package com.sbancuz.plannh.data.extractors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.FlowchartNode;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipePropertyExtractor;
import com.sbancuz.plannh.data.Settings;

import appeng.api.AEApi;
import appeng.api.features.IGrinderEntry;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

public class AE2Extractor implements RecipePropertyExtractor {

    public static final RecipeProperty<Integer> ENERGY_COST = RecipeProperty
        .intProperty("ae2.energyCost", "Energy Cost", 0);

    @Override
    public String getModId() {
        return Compat.AE2.modid;
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        RecipePropertyAPI.registerProperty(ENERGY_COST);
        MachineProfileRegistry.register(
            MachineProfile.builder("ae2:basic", "AE2 Grinder")
                .setting(Settings.MACHINES.def())
                .setting(Settings.ENERGY_PER_TICK.def())
                .effect(AE2Extractor::simpleEffect)
                .build());
    }

    @Override
    public String getProfileId(IRecipeHandler handler, int recipeIndex) {
        return "ae2:basic";
    }

    @Override
    public boolean canHandle(String recipeOwner) {
        return "grindstone".equals(recipeOwner);
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(FlowchartNode node, IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof TemplateRecipeHandler trh)) return props;

        List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);
        List<PositionedStack> ingredients = cached.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) return props;

        IGrinderEntry entry = AEApi.instance()
            .registries()
            .grinder()
            .getRecipeForInput(ingredients.getFirst().item);
        if (entry == null) return props;

        int energy = entry.getEnergyCost();
        if (energy > 0) {
            props.put(ENERGY_COST, energy);
        }

        return props;
    }

    private static MachineProfile.EffectResult simpleEffect(Map<String, Object> s, MachineProfile.RecipeContext ctx) {
        int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        int rate = MachineProfile.getInt(s, Settings.ENERGY_PER_TICK.key(), 10);
        Integer totalEnergy = ctx.get(AE2Extractor.ENERGY_COST);
        int duration = ctx.recipeDuration();
        if (duration <= 0 && rate > 0 && totalEnergy != null && totalEnergy > 0) {
            duration = Math.max(1, totalEnergy / rate);
        }
        long consumptionEUt = duration > 0 && totalEnergy != null ? totalEnergy / duration : 0;
        return new MachineProfile.EffectResult(duration, consumptionEUt, machines);
    }
}
