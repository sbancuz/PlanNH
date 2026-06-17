package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

    public static final RecipeProperty<Integer> MANA_COST = RecipeProperty.intBuilder("manaCost", 0)
        .build();

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(new RecipeHandlerFloatingFlowers().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new RecipeHandlerPetalApothecary().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new RecipeHandlerRunicAltar().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new RecipeHandlerManaPool().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new RecipeHandlerElvenTrade().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new RecipeHandlerBrewery().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new RecipeHandlerPureDaisy().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new RecipeHandlerLexicaBotania().getOverlayIdentifier(), this);

        RecipePropertyAPI.registerProperty(MANA_COST);

        MachineProfileRegistry.register(
            MachineProfile.builder("botania:basic", "Botania")
                .setting(Settings.MACHINES.def())
                .setting(Settings.MANA_PER_TICK.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(BotaniaProvider::simpleEffect)
                .build());
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
        final Map<RecipeProperty<?>, Object> props = new HashMap<>();
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

    @Nonnull
    private static MachineProfile.EffectResult simpleEffect(final Map<String, Object> s,
        final MachineProfile.RecipeContext ctx) {
        final int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        final int rate = MachineProfile.getInt(s, Settings.MANA_PER_TICK.key(), 10);
        final Integer totalEnergy = ctx.get(BotaniaProvider.MANA_COST);
        int duration = ctx.recipeDuration();
        if (duration <= 0 && rate > 0 && totalEnergy != null && totalEnergy > 0) {
            duration = Math.max(1, totalEnergy / rate);
        }
        final long consumptionEUt = duration > 0 && totalEnergy != null ? totalEnergy / duration : 0;
        return new MachineProfile.EffectResult(duration, consumptionEUt, machines);
    }
}
