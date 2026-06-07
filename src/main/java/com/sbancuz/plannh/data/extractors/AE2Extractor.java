package com.sbancuz.plannh.data.extractors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.FlowchartNode;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipePropertyExtractor;

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
        return "appliedenergistics2";
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        RecipePropertyAPI.registerProperty(ENERGY_COST);
    }

    @Override
    public boolean canHandle(String recipeOwner) {
        return "grindstone".equals(recipeOwner);
    }

    @Override
    public String getProfileId(IRecipeHandler handler, int recipeIndex) {
        return null;
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
            .getRecipeForInput(ingredients.get(0).item);
        if (entry == null) return props;

        int energy = entry.getEnergyCost();
        if (energy > 0) {
            props.put(ENERGY_COST, energy);
        }
        return props;
    }
}
