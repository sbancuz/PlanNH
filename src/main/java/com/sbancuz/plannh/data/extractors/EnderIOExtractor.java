package com.sbancuz.plannh.data.extractors;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipePropertyExtractor;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.flowchart.Node;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import crazypants.enderio.nei.AlloySmelterRecipeHandler.AlloySmelterRecipe;
import crazypants.enderio.nei.SagMillRecipeHandler.MillRecipe;
import crazypants.enderio.nei.SliceAndSpliceRecipeHandler.SliceAndSpliceRecipe;
import crazypants.enderio.nei.SoulBinderRecipeHandler.SoulBinderRecipeNEI;
import crazypants.enderio.nei.VatRecipeHandler.InnerVatRecipe;
import it.unimi.dsi.fastutil.objects.ObjectFloatImmutablePair;

public class EnderIOExtractor implements RecipePropertyExtractor {

    public static final RecipeProperty<Integer> RF_TOTAL = RecipeProperty.intProperty("rfTotal", "RF Total", 0);
    public static final RecipeProperty<Integer> EXPERIENCE = RecipeProperty.intProperty("experience", "Experience", 0);

    private static Field MILL_OUTPUT_CHANCE;

    @Override
    public String getModId() {
        return Compat.ENDERIO.modid;
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        RecipePropertyAPI.registerProperty(RF_TOTAL);
        RecipePropertyAPI.registerProperty(EXPERIENCE);
        MachineProfileRegistry.register(
            MachineProfile.builder("enderio", "EnderIO")
                .setting(Settings.MACHINES.def())
                .setting(Settings.RF_PER_TICK.def())
                .effect(EnderIOExtractor::enderIOEffect)
                .build());

        Field f = null;
        try {
            f = MillRecipe.class.getDeclaredField("outputChance");
            f.setAccessible(true);
        } catch (Exception ignored) {}
        MILL_OUTPUT_CHANCE = f;
    }

    @Override
    public String getProfileId(IRecipeHandler handler, int recipeIndex) {
        return "enderio";
    }

    @Override
    public boolean canHandle(String recipeOwner) {
        return recipeOwner != null && (recipeOwner.startsWith("EnderIO") || recipeOwner.equals("EIOEnchanter"));
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(Node node, IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof TemplateRecipeHandler trh)) return props;

        List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);

        if (cached instanceof AlloySmelterRecipe r) {
            props.put(RF_TOTAL, r.getEnergy());
        } else if (cached instanceof MillRecipe r) {
            props.put(RF_TOTAL, r.getEnergy());
            if (MILL_OUTPUT_CHANCE != null) {
                try {
                    float[] chances = (float[]) MILL_OUTPUT_CHANCE.get(r);
                    for (int i = 0; i < chances.length; i++) {
                        node.outputs.set(
                            i,
                            new ObjectFloatImmutablePair<>(
                                node.outputs.get(i)
                                    .left(),
                                chances[i]));
                    }
                } catch (Exception ignored) {}
            }
        } else if (cached instanceof SliceAndSpliceRecipe r) {
            props.put(RF_TOTAL, r.getEnergy());
        } else if (cached instanceof SoulBinderRecipeNEI r) {
            props.put(RF_TOTAL, r.getEnergy());
            if (r.getExperience() > 0) props.put(EXPERIENCE, r.getExperience());
        } else if (cached instanceof InnerVatRecipe r) {
            props.put(RF_TOTAL, r.getEnergy());
        }

        return props;
    }

    private static MachineProfile.EffectResult enderIOEffect(Map<String, Object> s, MachineProfile.RecipeContext ctx) {
        int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        int rate = MachineProfile.getInt(s, Settings.RF_PER_TICK.key(), 80);
        Integer totalEnergy = ctx.get(EnderIOExtractor.RF_TOTAL);
        int duration = ctx.recipeDuration();
        if (duration <= 0 && rate > 0 && totalEnergy != null && totalEnergy > 0) {
            duration = Math.max(1, totalEnergy / rate);
        }
        long consumptionEUt = duration > 0 && totalEnergy != null ? totalEnergy / duration : 0;
        return new MachineProfile.EffectResult(duration, consumptionEUt, machines);
    }
}
