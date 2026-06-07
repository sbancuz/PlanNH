package com.sbancuz.plannh.data.extractors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.FlowchartNode;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipePropertyExtractor;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.recipe.Sievert;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.GTNEIDefaultHandler.CachedDefaultRecipe;

public class BartWorksExtractor implements RecipePropertyExtractor {

    public static final RecipeProperty<Integer> GLASS_TIER = RecipeProperty
        .intProperty("bartworks.glassTier", "Glass Tier", 3);
    public static final RecipeProperty<Integer> SIEVERT = RecipeProperty.intProperty("bartworks.sievert", "Sievert", 0);
    public static final RecipeProperty<Boolean> SIEVERT_EXACT = RecipeProperty
        .boolProperty("bartworks.sievertExact", "Exact Sievert", false);
    public static final RecipeProperty<Integer> MASS = RecipeProperty.intProperty("bartworks.mass", "Mass", 0);

    @Override
    public String getModId() {
        return Compat.GREGTECH.modid;
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        RecipePropertyAPI.registerProperty(GLASS_TIER);
        RecipePropertyAPI.registerProperty(SIEVERT);
        RecipePropertyAPI.registerProperty(SIEVERT_EXACT);
        RecipePropertyAPI.registerProperty(MASS);
    }

    @Override
    public boolean canHandle(String recipeOwner) {
        if (recipeOwner == null) return false;
        return recipeOwner.equals("bw.recipe.biolab") || recipeOwner.equals("bw.recipe.BacteriaVat")
            || recipeOwner.equals("bw.recipe.radhatch");
    }

    @Override
    public String getProfileId(IRecipeHandler handler, int recipeIndex) {
        return null;
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(FlowchartNode node, IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof GTNEIDefaultHandler gth)) return props;

        List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(gth);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        CachedDefaultRecipe cached = (CachedDefaultRecipe) recipes.get(recipeIndex);
        GTRecipe r = cached.mRecipe;
        if (r == null) return props;

        int glassTier = r.getMetadataOrDefault(GTRecipeConstants.GLASS, 3);
        if (glassTier != 3) {
            props.put(GLASS_TIER, glassTier);
        }

        Sievert sievert = r.getMetadataOrDefault(GTRecipeConstants.SIEVERT, new Sievert(0, false));
        if (sievert.sievert > 0 || sievert.isExact) {
            props.put(SIEVERT, sievert.sievert);
            if (sievert.isExact) props.put(SIEVERT_EXACT, true);
        }

        int mass = r.getMetadataOrDefault(GTRecipeConstants.MASS, 0);
        if (mass > 0) {
            props.put(MASS, mass);
        }

        return props;
    }
}
