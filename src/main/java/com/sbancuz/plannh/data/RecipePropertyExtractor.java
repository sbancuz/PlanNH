package com.sbancuz.plannh.data;

import java.util.Map;

import codechicken.nei.recipe.IRecipeHandler;
import com.sbancuz.plannh.data.flowchart.Node;

public interface RecipePropertyExtractor {

    String getModId();

    void register();

    boolean canHandle(String recipeOwner);

    Map<RecipeProperty<?>, Object> extract(Node node, IRecipeHandler handler, int recipeIndex);

    default String getProfileId(IRecipeHandler handler, int recipeIndex) {
        return "vanilla";
    }
}
