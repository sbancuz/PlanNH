package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import tconstruct.library.crafting.CastingRecipe;
import tconstruct.plugins.nei.RecipeHandlerAlloying;
import tconstruct.plugins.nei.RecipeHandlerCastingBase;
import tconstruct.plugins.nei.RecipeHandlerCastingBasin;
import tconstruct.plugins.nei.RecipeHandlerCastingTable;
import tconstruct.plugins.nei.RecipeHandlerDryingRack;
import tconstruct.plugins.nei.RecipeHandlerDryingRack.CachedDryingRackRecipe;
import tconstruct.plugins.nei.RecipeHandlerMelting;
import tconstruct.plugins.nei.RecipeHandlerToolMaterials;

public final class TinkersConstructProvider implements PropertyProvider {

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(RecipeHandlerAlloying.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerCastingBasin.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerCastingTable.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerDryingRack.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerMelting.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerToolMaterials.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder("tconstruct:basic", "Tinkers' Construct")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(
                    Effects.clearCost()
                        .applyParallelism())
                .build());
    }

    @Override
    public boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        return getProfileId(handler, recipeIndex) != null;
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return switch (handler) {
            case RecipeHandlerAlloying _, RecipeHandlerCastingBasin _, RecipeHandlerCastingTable _,
                 RecipeHandlerDryingRack _, RecipeHandlerMelting _, RecipeHandlerToolMaterials _ -> "tconstruct:basic";
            default -> null;
        };
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>(
            PropertyProvider.super.extract(node, handler, recipeIndex));
        if (!(handler instanceof final TemplateRecipeHandler trh)) return props;

        final List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        final TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);

        if (cached instanceof final CachedDryingRackRecipe r && r.time > 0) {
            props.put(RecipePropertyAPI.DURATION_TICKS, r.time);
        }

        if (handler instanceof final RecipeHandlerCastingBase cb) {
            final List<CastingRecipe> castingRecipes = cb.getCastingRecipes();
            final var resultPos = cached.getResult();
            if (resultPos != null && resultPos.item != null) {
                CastingRecipe match = null;
                for (final CastingRecipe cr : castingRecipes) {
                    if (cr.output.isItemEqual(resultPos.item)) {
                        match = cr;
                        break;
                    }
                }
                if (match != null && match.coolTime > 0) {
                    props.put(RecipePropertyAPI.DURATION_TICKS, match.coolTime);
                }
            }
        }

        return props;
    }
}
