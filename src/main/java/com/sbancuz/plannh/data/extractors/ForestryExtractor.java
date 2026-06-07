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
import forestry.api.recipes.ICentrifugeRecipe;
import forestry.api.recipes.RecipeManagers;
import forestry.factory.recipes.nei.NEIHandlerCentrifuge;
import forestry.factory.recipes.nei.NEIHandlerCentrifuge.CachedCentrifugeRecipe;
import forestry.factory.recipes.nei.NEIHandlerSqueezer;
import forestry.factory.recipes.nei.NEIHandlerSqueezer.CachedSqueezerRecipe;

public class ForestryExtractor implements RecipePropertyExtractor {

    public static final RecipeProperty<Integer> PROCESSING_TIME = RecipeProperty
        .intProperty("forestry.processingTime", "Processing Time", 0);

    @Override
    public String getModId() {
        return "Forestry";
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        RecipePropertyAPI.registerProperty(PROCESSING_TIME);
    }

    @Override
    public boolean canHandle(String recipeOwner) {
        if (recipeOwner == null) return false;
        return recipeOwner.startsWith("forestry.");
    }

    @Override
    public String getProfileId(IRecipeHandler handler, int recipeIndex) {
        return "forestry:basic";
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(FlowchartNode node, IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof TemplateRecipeHandler trh)) return props;

        List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);

        if (handler instanceof NEIHandlerSqueezer && cached instanceof CachedSqueezerRecipe s) {
            if (s.processingTime > 0) {
                props.put(PROCESSING_TIME, s.processingTime);
            }
        } else if (handler instanceof NEIHandlerCentrifuge && cached instanceof CachedCentrifugeRecipe c) {
            int time = lookupCentrifugeTime(c.inputs.item);
            if (time > 0) props.put(PROCESSING_TIME, time);
        }

        return props;
    }

    private static int lookupCentrifugeTime(net.minecraft.item.ItemStack input) {
        if (input == null) return 0;
        for (ICentrifugeRecipe r : RecipeManagers.centrifugeManager.recipes()) {
            if (r.getInput() != null && r.getInput()
                .isItemEqual(input)) {
                return r.getProcessingTime();
            }
        }
        return 0;
    }
}
