package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.Map;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.flowchart.Node;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;

public class ThaumcraftProvider implements PropertyProvider {

    public static final RecipeProperty<int[]> VIS_COST = RecipeProperty
        .intArrayProperty("visCost", "Vis Cost", new int[6]);

    public static final RecipeProperty<Integer> INSTABILITY = RecipeProperty
        .intProperty("instability", "Instability", 0);

    public static final RecipeProperty<Integer> TOTAL_VIS = RecipeProperty.intProperty("totalVis", "Total Vis", 0);

    private static final String[] PRIMAL_TAGS = { "aer", "terra", "ignis", "aqua", "ordo", "perditio" };

    @Override
    public String getModId() {
        return Compat.THAUMCRAFT.modid;
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        RecipePropertyAPI.registerProperty(VIS_COST);
        RecipePropertyAPI.registerProperty(INSTABILITY);
        RecipePropertyAPI.registerProperty(TOTAL_VIS);
        MachineProfileRegistry.register(
            MachineProfile.builder("thaumcraft:basic", "Thaumcraft")
                .setting(Settings.MACHINES.def())
                .setting(Settings.VIS_PER_TICK.def())
                .effect(ThaumcraftProvider::simpleEffect)
                .build());
    }

    @Override
    public String getProfileId(IRecipeHandler handler, int recipeIndex) {
        if (!(handler instanceof TemplateRecipeHandler)) return null;
        String overlay = handler.getOverlayIdentifier();
        if (overlay == null) return null;
        if (overlay.equals("arcaneshapedrecipes") || overlay.equals("arcaneshapelessrecipes")
            || overlay.equals("cruciblerecipe") || overlay.equals("infusionCrafting")) {
            return "thaumcraft:basic";
        }
        return null;
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(Node node, IRecipeHandler handler, int recipeIndex) {
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
                if (cr != null && cr.aspects != null) {
                    props.put(VIS_COST, aspectListToPrimals(cr.aspects));
                    props.put(TOTAL_VIS, sumVis(cr.aspects));
                }
            }
            case "infusionCrafting" -> {
                InfusionRecipe ir = ThaumcraftApi.getInfusionRecipe(result);
                if (ir != null) {
                    AspectList aspects = ir.getAspects();
                    if (aspects != null) {
                        props.put(VIS_COST, aspectListToPrimals(aspects));
                        props.put(TOTAL_VIS, sumVis(aspects));
                    }
                    int instability = ir.getInstability();
                    if (instability > 0) props.put(INSTABILITY, instability);
                }
            }
            case "arcaneshapedrecipes", "arcaneshapelessrecipes" -> {
                AspectList aspects = findArcaneAspects(result);
                if (aspects != null) {
                    props.put(VIS_COST, aspectListToPrimals(aspects));
                    props.put(TOTAL_VIS, sumVis(aspects));
                }
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

    private static int sumVis(AspectList aspects) {
        if (aspects == null) return 0;
        int total = 0;
        for (Aspect a : aspects.getAspects()) {
            if (a != null) total += aspects.getAmount(a);
        }
        return total;
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

    private static MachineProfile.EffectResult simpleEffect(Map<String, Object> s, MachineProfile.RecipeContext ctx) {
        int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        int rate = MachineProfile.getInt(s, Settings.VIS_PER_TICK.key(), 1);
        Integer totalEnergy = ctx.get(ThaumcraftProvider.TOTAL_VIS);
        int duration = ctx.recipeDuration();
        if (duration <= 0 && rate > 0 && totalEnergy != null && totalEnergy > 0) {
            duration = Math.max(1, totalEnergy / rate);
        }
        long consumptionEUt = duration > 0 && totalEnergy != null ? totalEnergy / duration : 0;
        return new MachineProfile.EffectResult(duration, consumptionEUt, machines);
    }
}
