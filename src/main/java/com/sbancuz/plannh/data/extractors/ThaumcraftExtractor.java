package com.sbancuz.plannh.data.extractors;

import java.util.HashMap;
import java.util.Map;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.FlowchartNode;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipePropertyExtractor;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;

public class ThaumcraftExtractor implements RecipePropertyExtractor {

    public static final RecipeProperty<int[]> VIS_COST = RecipeProperty
        .intArrayProperty("visCost", "Vis Cost", new int[6]);

    public static final RecipeProperty<Integer> INSTABILITY = RecipeProperty
        .intProperty("instability", "Instability", 0);

    private static final String[] PRIMAL_TAGS = { "aer", "terra", "ignis", "aqua", "ordo", "perditio" };

    @Override
    public String getModId() {
        return "Thaumcraft";
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        RecipePropertyAPI.registerProperty(VIS_COST);
        RecipePropertyAPI.registerProperty(INSTABILITY);
    }

    @Override
    public boolean canHandle(String recipeOwner) {
        if (recipeOwner == null) return false;
        return recipeOwner.equals("arcaneshapedrecipes") || recipeOwner.equals("arcaneshapelessrecipes")
            || recipeOwner.equals("cruciblerecipe")
            || recipeOwner.equals("infusionCrafting");
    }

    @Override
    public String getProfileId(IRecipeHandler handler, int recipeIndex) {
        return null;
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(FlowchartNode node, IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof TemplateRecipeHandler trh)) return props;

        var recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);
        String overlay = trh.getOverlayIdentifier();
        if (overlay == null) return props;

        var resultPos = cached.getResult();
        if (resultPos == null || resultPos.item == null) return props;
        var result = resultPos.item;

        switch (overlay) {
            case "cruciblerecipe" -> {
                CrucibleRecipe cr = ThaumcraftApi.getCrucibleRecipe(result);
                if (cr != null && cr.aspects != null) props.put(VIS_COST, aspectListToPrimals(cr.aspects));
            }
            case "infusionCrafting" -> {
                InfusionRecipe ir = ThaumcraftApi.getInfusionRecipe(result);
                if (ir != null) {
                    AspectList aspects = ir.getAspects();
                    if (aspects != null) props.put(VIS_COST, aspectListToPrimals(aspects));
                    int instability = ir.getInstability();
                    if (instability > 0) props.put(INSTABILITY, instability);
                }
            }
            case "arcaneshapedrecipes", "arcaneshapelessrecipes" -> {
                AspectList aspects = findArcaneAspects(result);
                if (aspects != null) props.put(VIS_COST, aspectListToPrimals(aspects));
            }
        }

        return props;
    }

    private static AspectList findArcaneAspects(net.minecraft.item.ItemStack result) {
        for (Object obj : ThaumcraftApi.getCraftingRecipes()) {
            if (obj instanceof ShapedArcaneRecipe ar && result.isItemEqual(ar.getRecipeOutput()))
                return ar.getAspects();
            if (obj instanceof ShapelessArcaneRecipe ar && result.isItemEqual(ar.getRecipeOutput()))
                return ar.getAspects();
        }
        return null;
    }

    private static int[] aspectListToPrimals(AspectList aspects) {
        int[] result = new int[6];
        if (aspects == null) return result;
        Aspect[] aspectArray = aspects.getAspects();
        if (aspectArray == null) return result;
        for (Aspect aspect : aspectArray) {
            if (aspect == null) continue;
            String tag = aspect.getTag();
            if (tag == null) continue;
            for (int i = 0; i < 6; i++) {
                if (PRIMAL_TAGS[i].equals(tag)) {
                    result[i] = aspects.getAmount(aspect);
                    break;
                }
            }
        }
        return result;
    }
}
