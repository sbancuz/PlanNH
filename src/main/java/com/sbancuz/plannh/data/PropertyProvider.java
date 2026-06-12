package com.sbancuz.plannh.data;

import java.util.Map;

import com.sbancuz.plannh.data.flowchart.Node;

import codechicken.nei.recipe.IRecipeHandler;

public interface PropertyProvider {

    String getModId();

    void register();

    Map<RecipeProperty<?>, Object> extract(Node node, IRecipeHandler handler, int recipeIndex);

    default String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return null;
    }

    default boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        return true;
    }
}
