package com.sbancuz.plannh.data.extractors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipePropertyExtractor;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.GTNEIDefaultHandler.CachedDefaultRecipe;

public class GTExtractor implements RecipePropertyExtractor {

    public static final RecipeProperty<Integer> SPECIAL_VALUE = RecipeProperty
        .intProperty("specialValue", "Special Value", 0);

    static {
        RecipePropertyAPI.registerProperty(SPECIAL_VALUE);
    }

    @Override
    public String getModId() {
        return "gregtech";
    }

    @Override
    public boolean canHandle(String recipeOwner) {
        return recipeOwner != null && recipeOwner.startsWith("gt.recipe");
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof GTNEIDefaultHandler gth)) return props;

        List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(gth);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        CachedDefaultRecipe cached = (CachedDefaultRecipe) recipes.get(recipeIndex);
        GTRecipe r = cached.mRecipe;
        if (r == null) return props;

        int duration = r.mDuration;
        int eut = r.mEUt;

        props.put(RecipePropertyAPI.DURATION_TICKS, duration);
        props.put(RecipePropertyAPI.EU_PER_TICK, (long) eut);
        props.put(RecipePropertyAPI.TOTAL_EU, (long) eut * duration);

        if (r.mSpecialValue != 0) {
            props.put(SPECIAL_VALUE, r.mSpecialValue);
        }

        if (r.mOutputChances != null && r.mOutputChances.length > 0) {
            int[] raw = r.mOutputChances;
            float[] chances = new float[raw.length];
            for (int i = 0; i < raw.length; i++) {
                chances[i] = raw[i] / 10000f;
            }
            props.put(RecipePropertyAPI.OUTPUT_CHANCES, chances);
        }

        return props;
    }
}
