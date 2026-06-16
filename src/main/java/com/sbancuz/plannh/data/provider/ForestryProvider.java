package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

    public static final RecipeProperty<Integer> PROCESSING_TIME = RecipeProperty
        .intProperty("forestry.processingTime", "Processing Time", 0);

    @Override
    @Nonnull
    public String getModId() {
        return Compat.FORESTRY.modid;
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(new NEIHandlerBottler().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new NEIHandlerCarpenter().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new NEIHandlerCentrifuge().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new NEIHandlerFabricator().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new NEIHandlerFermenter().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new NEIHandlerMoistener().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new NEIHandlerSqueezer().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new NEIHandlerStill().getOverlayIdentifier(), this);

        RecipePropertyAPI.registerProperty(PROCESSING_TIME);

        MachineProfileRegistry.register(
            MachineProfile.builder("forestry:basic", "Forestry")
                .setting(Settings.MACHINES.def())
                .setting(Settings.FORESTRY_RF_PER_TICK.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(ForestryProvider::simpleEffect)
                .build());
    }

    @Nonnull
    private static MachineProfile.EffectResult simpleEffect(final Map<String, Object> s,
        final MachineProfile.RecipeContext ctx) {
        final int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        final int rate = MachineProfile.getInt(s, Settings.FORESTRY_RF_PER_TICK.key(), 10);
        return new MachineProfile.EffectResult(ctx.recipeDuration(), rate, machines);
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler, final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof final TemplateRecipeHandler trh)) return props;

        final List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        final TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);

        if (handler instanceof NEIHandlerSqueezer && cached instanceof final CachedSqueezerRecipe s) {
            if (s.processingTime > 0) {
                props.put(PROCESSING_TIME, s.processingTime);
                props.put(RecipePropertyAPI.DURATION_TICKS, s.processingTime);
            }
        } else if (handler instanceof NEIHandlerCentrifuge && cached instanceof final CachedCentrifugeRecipe c) {
            final int time = lookupCentrifugeTime(c.inputs.item);
            if (time > 0) {
                props.put(PROCESSING_TIME, time);
                props.put(RecipePropertyAPI.DURATION_TICKS, time);
            }
        }

        return props;
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
