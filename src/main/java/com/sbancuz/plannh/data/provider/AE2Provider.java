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

import appeng.api.AEApi;
import appeng.api.features.IGrinderEntry;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

public class AE2Provider implements PropertyProvider {

    public static final RecipeProperty<Integer> ENERGY_COST = RecipeProperty
        .intProperty("ae2.energyCost", "Energy Cost", 0);

    @Override
    public String getModId() {
        return Compat.AE2.modid;
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        RecipePropertyAPI.registerProperty(ENERGY_COST);
        MachineProfileRegistry.register(
            MachineProfile.builder("ae2:basic", "AE2 Grinder")
                .setting(Settings.MACHINES.def())
                .setting(Settings.ENERGY_PER_TICK.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(AE2Provider::simpleEffect)
                .build());
    }

    @Override
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        if (!(handler instanceof TemplateRecipeHandler)) return null;
        return "grindstone".equals(handler.getOverlayIdentifier()) ? "ae2:basic" : null;
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler, final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof final TemplateRecipeHandler trh)) return props;

        final List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        final TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);
        final List<PositionedStack> ingredients = cached.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) return props;

        final IGrinderEntry entry = AEApi.instance()
            .registries()
            .grinder()
            .getRecipeForInput(ingredients.getFirst().item);
        if (entry == null) return props;

        final int energy = entry.getEnergyCost();
        if (energy > 0) {
            props.put(ENERGY_COST, energy);
        }

        return props;
    }

    private static MachineProfile.EffectResult simpleEffect(final Map<String, Object> s,
        final MachineProfile.RecipeContext ctx) {
        final int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        final int rate = MachineProfile.getInt(s, Settings.ENERGY_PER_TICK.key(), 10);
        final Integer totalEnergy = ctx.get(AE2Provider.ENERGY_COST);
        int duration = ctx.recipeDuration();
        if (duration <= 0 && rate > 0 && totalEnergy != null && totalEnergy > 0) {
            duration = Math.max(1, totalEnergy / rate);
        }
        final long consumptionEUt = duration > 0 && totalEnergy != null ? totalEnergy / duration : 0;
        return new MachineProfile.EffectResult(duration, consumptionEUt, machines);
    }
}
