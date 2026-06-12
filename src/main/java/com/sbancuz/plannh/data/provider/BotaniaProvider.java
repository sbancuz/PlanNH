package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.List;
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
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerManaPool.CachedManaPoolRecipe;
import vazkii.botania.client.integration.nei.recipe.RecipeHandlerRunicAltar.CachedRunicAltarRecipe;

public class BotaniaProvider implements PropertyProvider {

    public static final RecipeProperty<Integer> MANA_COST = RecipeProperty.intProperty("manaCost", "Mana Cost", 0);

    @Override
    public String getModId() {
        return Compat.BOTANIA.modid;
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
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
    public String getProfileId(IRecipeHandler handler, int recipeIndex) {
        if (!handler.getClass()
            .getName()
            .startsWith("vazkii.botania")) return null;
        return "botania:basic";
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(Node node, IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof TemplateRecipeHandler trh)) return props;
        if (!handler.getClass()
            .getName()
            .startsWith("vazkii.botania")) return props;

        List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);

        if (cached instanceof CachedRunicAltarRecipe r) {
            if (r.manaUsage > 0) {
                props.put(MANA_COST, r.manaUsage);
            }
        } else if (cached instanceof CachedManaPoolRecipe r) {
            if (r.mana > 0) {
                props.put(MANA_COST, r.mana);
            }
        }

        return props;
    }

    private static MachineProfile.EffectResult simpleEffect(Map<String, Object> s, MachineProfile.RecipeContext ctx) {
        int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        int rate = MachineProfile.getInt(s, Settings.MANA_PER_TICK.key(), 10);
        Integer totalEnergy = ctx.get(BotaniaProvider.MANA_COST);
        int duration = ctx.recipeDuration();
        if (duration <= 0 && rate > 0 && totalEnergy != null && totalEnergy > 0) {
            duration = Math.max(1, totalEnergy / rate);
        }
        long consumptionEUt = duration > 0 && totalEnergy != null ? totalEnergy / duration : 0;
        return new MachineProfile.EffectResult(duration, consumptionEUt, machines);
    }
}
