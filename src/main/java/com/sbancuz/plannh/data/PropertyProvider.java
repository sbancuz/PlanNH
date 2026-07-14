package com.sbancuz.plannh.data;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import codechicken.nei.recipe.IRecipeHandler;
import com.sbancuz.plannh.data.flowchart.Node;

public interface PropertyProvider {

    void register();

    @Nullable
    Map<RecipeProperty<?>, Object> extract(Node node, IRecipeHandler handler, int recipeIndex);

    @Nullable
    default String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return null;
    }

    default boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        return true;
    }

    @Nonnull
    default String getExtractorName() {
        return getClass().getSimpleName();
    }
}
