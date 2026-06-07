package com.sbancuz.plannh.data.extractors;

import java.util.HashMap;
import java.util.Map;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipePropertyExtractor;

import codechicken.nei.recipe.IRecipeHandler;

public class VanillaExtractor implements RecipePropertyExtractor {

    @Override
    public String getModId() {
        return "vanilla";
    }

    @Override
    public boolean canHandle(String recipeOwner) {
        return recipeOwner == null || recipeOwner.isEmpty();
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        props.put(RecipePropertyAPI.DURATION_TICKS, 20);
        return props;
    }
}
