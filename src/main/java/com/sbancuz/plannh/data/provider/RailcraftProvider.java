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
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.effect.steps.CoFHCompat;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.setting.Settings;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import tonius.neiintegration.mods.railcraft.RecipeHandlerBlastFurnace;
import tonius.neiintegration.mods.railcraft.RecipeHandlerBlastFurnace.CachedBlastFurnaceRecipe;
import tonius.neiintegration.mods.railcraft.RecipeHandlerCokeOven;
import tonius.neiintegration.mods.railcraft.RecipeHandlerCokeOven.CachedCokeOvenRecipe;
import tonius.neiintegration.mods.railcraft.RecipeHandlerRockCrusher;
import tonius.neiintegration.mods.railcraft.RecipeHandlerRollingMachine;

// TODO: Make a PR to expose these values from NEI cached recipes
// Rock Crusher and Rolling Machine have no time/energy in their cached recipe classes
public class RailcraftProvider implements PropertyProvider {

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(RecipeHandlerCokeOven.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerBlastFurnace.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerRockCrusher.class, this);
        RecipePropertyAPI.registerExtractor(RecipeHandlerRollingMachine.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder("railcraft", "Railcraft")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(
                    Effects.durationFromHandler()
                        .amortizeCost(CoFHCompat.RF_COST)
                        .applyParallelism())
                .build());
    }

    @Override
    public boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        return getProfileId(handler, recipeIndex) != null;
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

        final var cached = recipes.get(recipeIndex);

        if (handler instanceof final RecipeHandlerCokeOven rco) {
            final var r = (CachedCokeOvenRecipe) cached;
            if (r.cookTime > 0) {
                props.put(RecipePropertyAPI.DURATION_TICKS, r.cookTime);
            }
        } else if (handler instanceof final RecipeHandlerBlastFurnace rbf) {
            final var r = (CachedBlastFurnaceRecipe) cached;
            if (r.cookTime > 0) {
                props.put(RecipePropertyAPI.DURATION_TICKS, r.cookTime);
            }
        }
        // Rock Crusher and Rolling Machine: no time/RF in cached recipe — values would be estimates

        return props;
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return switch (handler) {
            case RecipeHandlerCokeOven _, RecipeHandlerBlastFurnace _,
                 RecipeHandlerRockCrusher _, RecipeHandlerRollingMachine _ -> "railcraft";
            default -> null;
        };
    }
}
