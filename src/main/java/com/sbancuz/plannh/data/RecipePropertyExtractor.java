package com.sbancuz.plannh.data;

import java.util.Map;

import codechicken.nei.recipe.IRecipeHandler;

public interface RecipePropertyExtractor {

    String getModId();

    void register();

    boolean canHandle(String recipeOwner);

    Map<RecipeProperty<?>, Object> extract(FlowchartNode node, IRecipeHandler handler, int recipeIndex);
}
