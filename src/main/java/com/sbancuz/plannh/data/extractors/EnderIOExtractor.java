package com.sbancuz.plannh.data.extractors;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipePropertyExtractor;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import crazypants.enderio.nei.AlloySmelterRecipeHandler.AlloySmelterRecipe;
import crazypants.enderio.nei.SagMillRecipeHandler.MillRecipe;
import crazypants.enderio.nei.SliceAndSpliceRecipeHandler.SliceAndSpliceRecipe;
import crazypants.enderio.nei.SoulBinderRecipeHandler.SoulBinderRecipeNEI;
import crazypants.enderio.nei.VatRecipeHandler.InnerVatRecipe;

public class EnderIOExtractor implements RecipePropertyExtractor {

    public static final RecipeProperty<Integer> RF_TOTAL = RecipeProperty.intProperty("rfTotal", "RF Total", 0);
    public static final RecipeProperty<Integer> EXPERIENCE = RecipeProperty.intProperty("experience", "Experience", 0);

    private static final Field MILL_OUTPUT_CHANCE;

    static {
        RecipePropertyAPI.registerProperty(RF_TOTAL);
        RecipePropertyAPI.registerProperty(EXPERIENCE);

        Field f = null;
        try {
            f = MillRecipe.class.getDeclaredField("outputChance");
            f.setAccessible(true);
        } catch (Exception ignored) {}
        MILL_OUTPUT_CHANCE = f;
    }

    @Override
    public String getModId() {
        return "enderio";
    }

    @Override
    public boolean canHandle(String recipeOwner) {
        return recipeOwner != null && (recipeOwner.startsWith("EnderIO") || recipeOwner.equals("EIOEnchanter"));
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof TemplateRecipeHandler trh)) return props;

        List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);

        if (cached instanceof AlloySmelterRecipe r) {
            props.put(RF_TOTAL, r.getEnergy());
        } else if (cached instanceof MillRecipe r) {
            props.put(RF_TOTAL, r.getEnergy());
            if (MILL_OUTPUT_CHANCE != null) {
                try {
                    float[] chances = (float[]) MILL_OUTPUT_CHANCE.get(r);
                    if (chances.length > 0) {
                        props.put(RecipePropertyAPI.OUTPUT_CHANCES, chances);
                    }
                } catch (Exception ignored) {}
            }
        } else if (cached instanceof SliceAndSpliceRecipe r) {
            props.put(RF_TOTAL, r.getEnergy());
        } else if (cached instanceof SoulBinderRecipeNEI r) {
            props.put(RF_TOTAL, r.getEnergy());
            if (r.getExperience() > 0) props.put(EXPERIENCE, r.getExperience());
        } else if (cached instanceof InnerVatRecipe r) {
            props.put(RF_TOTAL, r.getEnergy());
        }

        return props;
    }
}
