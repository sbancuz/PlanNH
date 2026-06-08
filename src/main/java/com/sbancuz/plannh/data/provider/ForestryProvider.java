package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import forestry.factory.recipes.nei.NEIHandlerCentrifuge;
import forestry.factory.recipes.nei.NEIHandlerCentrifuge.CachedCentrifugeRecipe;
import forestry.factory.recipes.nei.NEIHandlerSqueezer;
import forestry.factory.recipes.nei.NEIHandlerSqueezer.CachedSqueezerRecipe;

public class ForestryProvider implements PropertyProvider {

    public static final RecipeProperty<Integer> PROCESSING_TIME = RecipeProperty
        .intProperty("forestry.processingTime", "Processing Time", 0);

    @Override
    public String getModId() {
        return Compat.FORESTRY.modid;
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        RecipePropertyAPI.registerProperty(PROCESSING_TIME);
        MachineProfileRegistry.register(
            MachineProfile.builder("forestry:basic", "Forestry")
                .setting(Settings.MACHINES.def())
                .setting(Settings.FORESTRY_RF_PER_TICK.def())
                .effect(ForestryProvider::simpleEffect)
                .build());
    }

    private static MachineProfile.EffectResult simpleEffect(Map<String, Object> s, MachineProfile.RecipeContext ctx) {
        int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        int rate = MachineProfile.getInt(s, Settings.FORESTRY_RF_PER_TICK.key(), 10);
        return new MachineProfile.EffectResult(ctx.recipeDuration(), rate, machines);
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(Node node, IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof TemplateRecipeHandler trh)) return props;

        List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);

        if (handler instanceof NEIHandlerSqueezer && cached instanceof CachedSqueezerRecipe s) {
            if (s.processingTime > 0) {
                props.put(PROCESSING_TIME, s.processingTime);
                props.put(RecipePropertyAPI.DURATION_TICKS, s.processingTime);
            }
        } else if (handler instanceof NEIHandlerCentrifuge && cached instanceof CachedCentrifugeRecipe c) {
            int time = lookupCentrifugeTime(c.inputs.item);
            if (time > 0) {
                props.put(PROCESSING_TIME, time);
                props.put(RecipePropertyAPI.DURATION_TICKS, time);
            }
        }

        return props;
    }

    private static int lookupCentrifugeTime(ItemStack input) {
        if (input == null) return 0;
        for (ICentrifugeRecipe r : RecipeManagers.centrifugeManager.recipes()) {
            if (r.getInput() != null && r.getInput()
                .isItemEqual(input)) {
                return r.getProcessingTime();
            }
        }
        return 0;
    }
}
