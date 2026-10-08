package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.effect.steps.CoFHCompat;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import forestry.api.recipes.ICentrifugeRecipe;
import forestry.api.recipes.RecipeManagers;
import forestry.factory.recipes.nei.NEIHandlerBottler;
import forestry.factory.recipes.nei.NEIHandlerCarpenter;
import forestry.factory.recipes.nei.NEIHandlerCentrifuge;
import forestry.factory.recipes.nei.NEIHandlerCentrifuge.CachedCentrifugeRecipe;
import forestry.factory.recipes.nei.NEIHandlerFabricator;
import forestry.factory.recipes.nei.NEIHandlerFermenter;
import forestry.factory.recipes.nei.NEIHandlerMoistener;
import forestry.factory.recipes.nei.NEIHandlerSqueezer;
import forestry.factory.recipes.nei.NEIHandlerSqueezer.CachedSqueezerRecipe;
import forestry.factory.recipes.nei.NEIHandlerStill;

public class ForestryProvider implements PropertyProvider {

    private static final String PROFILE_ID = "forestry:basic";

    private static final int GAME_TICKS_PER_WORK_TICK = 5;

    // TODO: Make a PR to expose these constants
    // TileBottler: TICKS_PER_RECIPE_TIME=5, ENERGY_PER_RECIPE_TIME=1000
    private static final int BOTTLER_TICKS = 5;
    private static final int BOTTLER_RF = 1000;

    // TileCentrifuge: ENERGY_PER_RECIPE_TIME=160
    private static final int CENTRIFUGE_RF_PER_WORK_TICK = 160;

    // TileSqueezer: ENERGY_PER_RECIPE_TIME=200
    private static final int SQUEEZER_RF_PER_WORK_TICK = 200;

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(NEIHandlerBottler.class, this);
        RecipePropertyAPI.registerExtractor(NEIHandlerCarpenter.class, this);
        RecipePropertyAPI.registerExtractor(NEIHandlerCentrifuge.class, this);
        RecipePropertyAPI.registerExtractor(NEIHandlerFabricator.class, this);
        RecipePropertyAPI.registerExtractor(NEIHandlerFermenter.class, this);
        RecipePropertyAPI.registerExtractor(NEIHandlerMoistener.class, this);
        RecipePropertyAPI.registerExtractor(NEIHandlerSqueezer.class, this);
        RecipePropertyAPI.registerExtractor(NEIHandlerStill.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder(PROFILE_ID, "Forestry")
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
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return switch (handler) {
            case NEIHandlerBottler _, NEIHandlerCarpenter _, NEIHandlerCentrifuge _,
                 NEIHandlerFabricator _, NEIHandlerFermenter _, NEIHandlerMoistener _,
                 NEIHandlerSqueezer _, NEIHandlerStill _ -> PROFILE_ID;
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

        switch (handler) {
            case NEIHandlerBottler _ -> {
                props.put(RecipePropertyAPI.DURATION_TICKS, BOTTLER_TICKS);
                props.put(CoFHCompat.RF_COST, (long) BOTTLER_RF);
            }
            case NEIHandlerCentrifuge _ when cached instanceof final CachedCentrifugeRecipe c ->
                extractCentrifuge(props, c);
            case NEIHandlerSqueezer _ when cached instanceof final CachedSqueezerRecipe s ->
                extractSqueezer(props, s);
            default -> {}
        }

        return props;
    }

    private static void extractSqueezer(final Map<RecipeProperty<?>, Object> props, final CachedSqueezerRecipe s) {
        if (s.processingTime <= 0) return;
        props.put(RecipePropertyAPI.DURATION_TICKS, s.processingTime * GAME_TICKS_PER_WORK_TICK);
        props.put(CoFHCompat.RF_COST, (long) s.processingTime * SQUEEZER_RF_PER_WORK_TICK);
    }

    private static void extractCentrifuge(final Map<RecipeProperty<?>, Object> props, final CachedCentrifugeRecipe c) {
        final int workTicks = lookupCentrifugeTime(c.inputs.item);
        if (workTicks <= 0) return;
        props.put(RecipePropertyAPI.DURATION_TICKS, workTicks * GAME_TICKS_PER_WORK_TICK);
        props.put(CoFHCompat.RF_COST, (long) workTicks * CENTRIFUGE_RF_PER_WORK_TICK);
    }

    private static int lookupCentrifugeTime(final @Nullable ItemStack input) {
        if (input == null) return 0;
        for (final ICentrifugeRecipe r : RecipeManagers.centrifugeManager.recipes()) {
            if (r.getInput() != null && r.getInput()
                .isItemEqual(input)) {
                return r.getProcessingTime();
            }
        }
        return 0;
    }
}
