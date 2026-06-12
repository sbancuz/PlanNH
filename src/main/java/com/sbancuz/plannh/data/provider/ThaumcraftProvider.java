package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

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
import thaumcraft.api.ThaumcraftApiHelper;
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

    public static final RecipeProperty<String> RESEARCH_KEY = RecipeProperty
        .stringProperty("researchKey", "Research Key", "");

    public static final RecipeProperty<Integer> NUM_COMPONENTS = RecipeProperty
        .intProperty("numComponents", "Components", 0);

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
        RecipePropertyAPI.registerProperty(RESEARCH_KEY);
        RecipePropertyAPI.registerProperty(NUM_COMPONENTS);
        MachineProfileRegistry.register(
            MachineProfile.builder("thaumcraft:arcane", "Arcane Workbench")
                .setting(Settings.MACHINES.def())
                .setting(Settings.VIS_PER_TICK.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(ThaumcraftProvider::arcaneEffect)
                .build());
        MachineProfileRegistry.register(
            MachineProfile.builder("thaumcraft:infusion", "Infusion Altar")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(ThaumcraftProvider::infusionEffect)
                .build());
    }

    @Override
    public String getProfileId(IRecipeHandler handler, int recipeIndex) {
        if (!(handler instanceof TemplateRecipeHandler)) return null;
        String overlay = handler.getOverlayIdentifier();
        if (overlay == null) return null;
        return switch (overlay) {
            case "thaumcraft.arcane.shaped", "thaumcraft.arcane.shapeless", "thaumcraft.wands", "thaumcraft.alchemy" -> "thaumcraft:arcane";
            case "thaumcraft.infusion" -> "thaumcraft:infusion";
            default -> null;
        };
    }

    @Override
    public boolean canCraft(IRecipeHandler handler, int recipeIndex) {
        String researchKey = findResearchKey(handler, recipeIndex);
        if (researchKey == null || researchKey.isEmpty()) return false;
        return ThaumcraftApiHelper.isResearchComplete(
            Minecraft.getMinecraft()
                .getSession()
                .getUsername(),
            researchKey);
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
            case "thaumcraft.alchemy" -> {
                CrucibleRecipe cr = ThaumcraftApi.getCrucibleRecipe(result);
                if (cr != null && cr.aspects != null) {
                    props.put(VIS_COST, aspectListToPrimals(cr.aspects));
                    props.put(TOTAL_VIS, sumVis(cr.aspects));
                }
            }
            case "thaumcraft.infusion" -> {
                InfusionRecipe ir = ThaumcraftApi.getInfusionRecipe(result);
                if (ir != null) {
                    AspectList aspects = ir.getAspects();
                    if (aspects != null) {
                        props.put(VIS_COST, aspectListToPrimals(aspects));
                        props.put(TOTAL_VIS, sumVis(aspects));
                    }
                    int instability = ir.getInstability();
                    if (instability > 0) props.put(INSTABILITY, instability);
                    ItemStack[] comps = ir.getComponents();
                    props.put(NUM_COMPONENTS, comps != null ? comps.length : 0);
                }
            }
            case "thaumcraft.arcane.shaped", "thaumcraft.arcane.shapeless", "thaumcraft.wands" -> {
                for (Object obj : ThaumcraftApi.getCraftingRecipes()) {
                    if (obj instanceof ShapedArcaneRecipe ar && result.isItemEqual(ar.getRecipeOutput())) {
                        AspectList aspects = ar.getAspects();
                        if (aspects != null) {
                            props.put(VIS_COST, aspectListToPrimals(aspects));
                            props.put(TOTAL_VIS, sumVis(aspects));
                        }
                        break;
                    }
                    if (obj instanceof ShapelessArcaneRecipe ar && result.isItemEqual(ar.getRecipeOutput())) {
                        AspectList aspects = ar.getAspects();
                        if (aspects != null) {
                            props.put(VIS_COST, aspectListToPrimals(aspects));
                            props.put(TOTAL_VIS, sumVis(aspects));
                        }
                        break;
                    }
                }
            }
        }

        String researchKey = findResearchKey(handler, recipeIndex);
        if (researchKey != null) props.put(RESEARCH_KEY, researchKey);

        return props;
    }

    private static String findResearchKey(IRecipeHandler handler, int recipeIndex) {
        if (!(handler instanceof TemplateRecipeHandler trh)) return null;
        String overlay = trh.getOverlayIdentifier();
        if (overlay == null) return null;
        if (!switch (overlay) {
            case "thaumcraft.arcane.shaped", "thaumcraft.arcane.shapeless", "thaumcraft.wands", "thaumcraft.alchemy", "thaumcraft.infusion" -> true;
            default -> false;
        }) return null;

        var recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return null;

        TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);
        var resultPos = cached.getResult();
        if (resultPos == null || resultPos.item == null) return null;
        var result = resultPos.item;

        return switch (overlay) {
            case "thaumcraft.alchemy" -> {
                CrucibleRecipe cr = ThaumcraftApi.getCrucibleRecipe(result);
                yield cr != null ? cr.key : null;
            }
            case "thaumcraft.infusion" -> {
                InfusionRecipe ir = ThaumcraftApi.getInfusionRecipe(result);
                yield ir != null ? ir.getResearch() : null;
            }
            case "thaumcraft.arcane.shaped", "thaumcraft.arcane.shapeless", "thaumcraft.wands" -> {
                String key = null;
                for (Object obj : ThaumcraftApi.getCraftingRecipes()) {
                    if (obj instanceof ShapedArcaneRecipe ar && result.isItemEqual(ar.getRecipeOutput())) {
                        key = ar.getResearch();
                        break;
                    }
                    if (obj instanceof ShapelessArcaneRecipe ar && result.isItemEqual(ar.getRecipeOutput())) {
                        key = ar.getResearch();
                        break;
                    }
                }
                yield key;
            }
            default -> null;
        };
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

    private static MachineProfile.EffectResult arcaneEffect(Map<String, Object> s, MachineProfile.RecipeContext ctx) {
        int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        int rate = MachineProfile.getInt(s, Settings.VIS_PER_TICK.key(), 1);
        Integer totalVis = ctx.get(ThaumcraftProvider.TOTAL_VIS);
        int duration = ctx.recipeDuration();
        if (duration <= 0 && rate > 0 && totalVis != null && totalVis > 0) {
            duration = Math.max(1, totalVis / rate);
        }
        long consumptionEUt = duration > 0 && totalVis != null ? totalVis / duration : 0;
        return new MachineProfile.EffectResult(duration, consumptionEUt, machines);
    }

    private static MachineProfile.EffectResult infusionEffect(Map<String, Object> s, MachineProfile.RecipeContext ctx) {
        int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        Integer totalVis = ctx.get(ThaumcraftProvider.TOTAL_VIS);
        Integer numComponents = ctx.get(ThaumcraftProvider.NUM_COMPONENTS);
        int nc = numComponents != null ? numComponents : 0;
        int duration = ctx.recipeDuration();
        if (duration <= 0) {
            int tv = totalVis != null ? totalVis : 0;
            duration = Math.max(1, tv * 10 + nc * 60);
        }
        long consumptionEUt = duration > 0 && totalVis != null ? totalVis / duration : 0;
        return new MachineProfile.EffectResult(duration, consumptionEUt, machines);
    }
}
