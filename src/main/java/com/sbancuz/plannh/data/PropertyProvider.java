package com.sbancuz.plannh.data;

import java.util.Map;

import com.sbancuz.plannh.data.flowchart.Node;

import codechicken.nei.recipe.IRecipeHandler;

public interface PropertyProvider {

    String getModId();

    void register();

    Map<RecipeProperty<?>, Object> extract(Node node, IRecipeHandler handler, int recipeIndex);

    default String getProfileId(IRecipeHandler handler, int recipeIndex) {
        return "vanilla";
    }
}
