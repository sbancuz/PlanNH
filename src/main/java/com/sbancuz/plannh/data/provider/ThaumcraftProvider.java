package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.aspectrecipeindex.nei.AlchemyRecipeHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.AspectCombinationHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.InfusionRecipeHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.ItemsContainingAspectHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.arcaneworkbench.ShapedArcaneRecipeHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.arcaneworkbench.ShapelessArcaneRecipeHandler;
import com.gtnewhorizons.aspectrecipeindex.nei.arcaneworkbench.WandRecipeHandler;
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

    public static final RecipeProperty<int[]> VIS_COST = RecipeProperty.<int[]>builder("vis_cost", new int[6])
        .build();

    public static final RecipeProperty<Integer> INSTABILITY = RecipeProperty.<Integer>builder("instability", 0)
        .build();

    public static final RecipeProperty<Integer> TOTAL_VIS = RecipeProperty.<Integer>builder("total_vis", 0)
        .build();

    public static final RecipeProperty<String> RESEARCH_KEY = RecipeProperty.<String>builder("research_key", "")
        .build();

    public static final RecipeProperty<Integer> NUM_COMPONENTS = RecipeProperty.<Integer>builder("num_components", 0)
        .build();

    private static final String[] PRIMAL_TAGS = { "aer", "terra", "ignis", "aqua", "ordo", "perditio" };

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(ItemsContainingAspectHandler.OVERLAY, this);
        RecipePropertyAPI.registerExtractor(AspectCombinationHandler.OVERLAY, this);
        RecipePropertyAPI.registerExtractor(ShapedArcaneRecipeHandler.OVERLAY, this);
        RecipePropertyAPI.registerExtractor(WandRecipeHandler.OVERLAY, this);
        RecipePropertyAPI.registerExtractor(ShapelessArcaneRecipeHandler.OVERLAY, this);
        RecipePropertyAPI.registerExtractor(AlchemyRecipeHandler.OVERLAY, this);
        RecipePropertyAPI.registerExtractor(InfusionRecipeHandler.OVERLAY, this);

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
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        if (!(handler instanceof TemplateRecipeHandler)) return null;
        final String overlay = handler.getOverlayIdentifier();
        if (overlay == null) return null;
        return switch (overlay) {
            case "thaumcraft.arcane.shaped", "thaumcraft.arcane.shapeless", "thaumcraft.wands", "thaumcraft.alchemy" -> "thaumcraft:arcane";
            case "thaumcraft.infusion" -> "thaumcraft:infusion";
            default -> null;
        };
    }

    @Override
    public boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        final String researchKey = findResearchKey(handler, recipeIndex);
        if (researchKey == null || researchKey.isEmpty()) return false;
        return ThaumcraftApiHelper.isResearchComplete(
            Minecraft.getMinecraft()
                .getSession()
                .getUsername(),
            researchKey);
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler, final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof final TemplateRecipeHandler trh)) return props;

        final var recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        final TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);
        final String overlay = trh.getOverlayIdentifier();
        if (overlay == null) return props;

        final var resultPos = cached.getResult();
        if (resultPos == null || resultPos.item == null) return props;
        final var result = resultPos.item;

        switch (overlay) {
            case "thaumcraft.alchemy" -> {
                final CrucibleRecipe cr = ThaumcraftApi.getCrucibleRecipe(result);
                if (cr != null && cr.aspects != null) {
                    props.put(VIS_COST, aspectListToPrimals(cr.aspects));
                    props.put(TOTAL_VIS, sumVis(cr.aspects));
                }
            }
            case "thaumcraft.infusion" -> {
                final InfusionRecipe ir = ThaumcraftApi.getInfusionRecipe(result);
                if (ir != null) {
                    final AspectList aspects = ir.getAspects();
                    if (aspects != null) {
                        props.put(VIS_COST, aspectListToPrimals(aspects));
                        props.put(TOTAL_VIS, sumVis(aspects));
                    }
                    final int instability = ir.getInstability();
                    if (instability > 0) props.put(INSTABILITY, instability);
                    final ItemStack[] comps = ir.getComponents();
                    props.put(NUM_COMPONENTS, comps != null ? comps.length : 0);
                }
            }
            case "thaumcraft.arcane.shaped", "thaumcraft.arcane.shapeless", "thaumcraft.wands" -> {
                for (final Object obj : ThaumcraftApi.getCraftingRecipes()) {
                    if (obj instanceof final ShapedArcaneRecipe ar && result.isItemEqual(ar.getRecipeOutput())) {
                        final AspectList aspects = ar.getAspects();
                        if (aspects != null) {
                            props.put(VIS_COST, aspectListToPrimals(aspects));
                            props.put(TOTAL_VIS, sumVis(aspects));
                        }
                        break;
                    }
                    if (obj instanceof final ShapelessArcaneRecipe ar && result.isItemEqual(ar.getRecipeOutput())) {
                        final AspectList aspects = ar.getAspects();
                        if (aspects != null) {
                            props.put(VIS_COST, aspectListToPrimals(aspects));
                            props.put(TOTAL_VIS, sumVis(aspects));
                        }
                        break;
                    }
                }
            }
        }

        final String researchKey = findResearchKey(handler, recipeIndex);
        if (researchKey != null) props.put(RESEARCH_KEY, researchKey);

        return props;
    }

    @Nullable
    private static String findResearchKey(final IRecipeHandler handler, final int recipeIndex) {
        if (!(handler instanceof final TemplateRecipeHandler trh)) return null;
        final String overlay = trh.getOverlayIdentifier();
        if (overlay == null) return null;
        if (!switch (overlay) {
            case "thaumcraft.arcane.shaped", "thaumcraft.arcane.shapeless", "thaumcraft.wands", "thaumcraft.alchemy", "thaumcraft.infusion" -> true;
            default -> false;
        }) return null;

        final var recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return null;

        final TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);
        final var resultPos = cached.getResult();
        if (resultPos == null || resultPos.item == null) return null;
        final var result = resultPos.item;

        return switch (overlay) {
            case "thaumcraft.alchemy" -> {
                final CrucibleRecipe cr = ThaumcraftApi.getCrucibleRecipe(result);
                yield cr != null ? cr.key : null;
            }
            case "thaumcraft.infusion" -> {
                final InfusionRecipe ir = ThaumcraftApi.getInfusionRecipe(result);
                yield ir != null ? ir.getResearch() : null;
            }
            case "thaumcraft.arcane.shaped", "thaumcraft.arcane.shapeless", "thaumcraft.wands" -> {
                String key = null;
                for (final Object obj : ThaumcraftApi.getCraftingRecipes()) {
                    if (obj instanceof final ShapedArcaneRecipe ar && result.isItemEqual(ar.getRecipeOutput())) {
                        key = ar.getResearch();
                        break;
                    }
                    if (obj instanceof final ShapelessArcaneRecipe ar && result.isItemEqual(ar.getRecipeOutput())) {
                        key = ar.getResearch();
                        break;
                    }
                }
                yield key;
            }
            default -> null;
        };
    }

    private static int sumVis(final @Nullable AspectList aspects) {
        if (aspects == null) return 0;
        int total = 0;
        for (final Aspect a : aspects.getAspects()) {
            if (a != null) total += aspects.getAmount(a);
        }
        return total;
    }

    @Nonnull
    private static int[] aspectListToPrimals(final @Nullable AspectList aspects) {
        final int[] result = new int[6];
        if (aspects == null) return result;
        final Aspect[] aspectArray = aspects.getAspects();
        if (aspectArray == null) return result;
        for (final Aspect aspect : aspectArray) {
            if (aspect == null) continue;
            final String tag = aspect.getTag();
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

    @Nonnull
    private static MachineProfile.EffectResult arcaneEffect(final Map<String, Object> s,
        final MachineProfile.RecipeContext ctx) {
        final int rate = MachineProfile.getInt(s, Settings.VIS_PER_TICK.key(), 1);
        final Integer totalVis = ctx.get(ThaumcraftProvider.TOTAL_VIS);
        int duration = ctx.recipeDuration();
        if (duration <= 0 && rate > 0 && totalVis != null && totalVis > 0) {
            duration = Math.max(1, totalVis / rate);
        }
        final long consumptionEUt = duration > 0 && totalVis != null ? totalVis / duration : 0;
        return new MachineProfile.EffectResult(duration, consumptionEUt, 1);
    }

    @Nonnull
    private static MachineProfile.EffectResult infusionEffect(final Map<String, Object> s,
        final MachineProfile.RecipeContext ctx) {
        final Integer totalVis = ctx.get(ThaumcraftProvider.TOTAL_VIS);
        final Integer numComponents = ctx.get(ThaumcraftProvider.NUM_COMPONENTS);
        final int nc = numComponents != null ? numComponents : 0;
        int duration = ctx.recipeDuration();
        if (duration <= 0) {
            final int tv = totalVis != null ? totalVis : 0;
            duration = Math.max(1, tv * 10 + nc * 60);
        }
        final long consumptionEUt = totalVis != null ? totalVis / duration : 0;
        return new MachineProfile.EffectResult(duration, consumptionEUt, 1);
    }
}
