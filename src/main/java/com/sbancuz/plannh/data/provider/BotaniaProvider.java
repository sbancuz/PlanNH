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
import com.sbancuz.plannh.data.properties.SummaryProperty;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerBrewery;
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerElvenTrade;
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerFloatingFlowers;
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerLexicaBotania;
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerManaPool;
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerManaPool.CachedManaPoolRecipe;
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerPetalApothecary;
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerPureDaisy;
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerRunicAltar;
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerRunicAltar.CachedRunicAltarRecipe;

public class BotaniaProvider implements PropertyProvider {

    public static final RecipeProperty<Integer> MANA_COST = SummaryProperty.builder("botania.mana_cost", 0)
        .build();

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(RecipeHandlerFloatingFlowers.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerPetalApothecary.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerRunicAltar.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerManaPool.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerElvenTrade.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerBrewery.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerPureDaisy.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerLexicaBotania.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder("botania:basic", "Botania")
                .setting(Settings.MACHINES.def())
                .setting(Settings.MANA_PER_TICK.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(
                    Effects.durationFromTotal(MANA_COST, Settings.MANA_PER_TICK.key(), 10)
                        .amortizeCost(MANA_COST)
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
        if (!handler.getClass()
            .getName()
            .startsWith("vazkii.botania")) return null;
        return "botania:basic";
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler, final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>(PropertyProvider.super.extract(node, handler, recipeIndex));

        if (!(handler instanceof final TemplateRecipeHandler trh)) return props;

        final List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        final TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);

        if (cached instanceof final CachedRunicAltarRecipe r) {
            if (r.manaUsage > 0) {
                props.put(MANA_COST, r.manaUsage);
            }
        } else if (cached instanceof final CachedManaPoolRecipe r) {
            if (r.mana > 0) {
                props.put(MANA_COST, r.mana);
            }
        }

        return props;
    }
}
