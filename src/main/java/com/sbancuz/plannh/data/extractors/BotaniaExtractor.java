package com.sbancuz.plannh.data.extractors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.FlowchartNode;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipePropertyExtractor;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerManaPool.CachedManaPoolRecipe;
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerRunicAltar.CachedRunicAltarRecipe;

public class BotaniaExtractor implements RecipePropertyExtractor {

    public static final RecipeProperty<Integer> MANA_COST = RecipeProperty.intProperty("manaCost", "Mana Cost", 0);

    @Override
    public String getModId() {
        return "Botania";
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        RecipePropertyAPI.registerProperty(MANA_COST);
    }

    @Override
    public boolean canHandle(String recipeOwner) {
        return recipeOwner == null;
    }

    @Override
    public String getProfileId(IRecipeHandler handler, int recipeIndex) {
        return null;
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(FlowchartNode node, IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof TemplateRecipeHandler trh)) return props;
        if (!handler.getClass()
            .getName()
            .startsWith("vazkii.botania")) return props;

        List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);

        if (cached instanceof CachedRunicAltarRecipe r) {
            if (r.manaUsage > 0) {
                props.put(MANA_COST, r.manaUsage);
            }
        } else if (cached instanceof CachedManaPoolRecipe r) {
            if (r.mana > 0) {
                props.put(MANA_COST, r.mana);
            }
        }

        return props;
    }
}
