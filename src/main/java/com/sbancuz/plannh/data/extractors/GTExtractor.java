package com.sbancuz.plannh.data.extractors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.FlowchartNode;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipePropertyExtractor;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.GTNEIDefaultHandler.CachedDefaultRecipe;
import it.unimi.dsi.fastutil.objects.ObjectFloatImmutablePair;

public class GTExtractor implements RecipePropertyExtractor {

    public static final RecipeProperty<Integer> SPECIAL_VALUE = RecipeProperty
        .intProperty("specialValue", "Special Value", 0);

    @Override
    public String getModId() {
        return Compat.GREGTECH.modid;
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(this);
        RecipePropertyAPI.registerProperty(SPECIAL_VALUE);
    }

    @Override
    public boolean canHandle(String recipeOwner) {
        return recipeOwner != null && recipeOwner.startsWith("gt.recipe");
    }

    @Override
    public Map<RecipeProperty<?>, Object> extract(FlowchartNode node, IRecipeHandler handler, int recipeIndex) {
        Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof GTNEIDefaultHandler gth)) return props;

        List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(gth);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        CachedDefaultRecipe cached = (CachedDefaultRecipe) recipes.get(recipeIndex);
        GTRecipe r = cached.mRecipe;
        if (r == null) return props;

        int duration = r.mDuration;
        int eut = r.mEUt;

        props.put(RecipePropertyAPI.DURATION_TICKS, duration);
        props.put(RecipePropertyAPI.EU_PER_TICK, (long) eut);
        props.put(RecipePropertyAPI.TOTAL_EU, (long) eut * duration);

        if (r.mSpecialValue != 0) {
            props.put(SPECIAL_VALUE, r.mSpecialValue);
        }

        if (r.mInputChances != null) {
            for (int i = 0; i < r.mInputs.length; i++) {
                node.inputs.set(
                    i,
                    new ObjectFloatImmutablePair<>(
                        node.inputs.get(i)
                            .left(),
                        r.mInputChances[i]));
            }
        }
        if (r.mOutputChances != null) {
            for (int i = 0; i < r.mOutputs.length; i++) {
                node.outputs.set(
                    i,
                    new ObjectFloatImmutablePair<>(
                        node.outputs.get(i)
                            .left(),
                        r.mOutputChances[i]));
            }
        }

        for (int i = 0; i < r.mFluidInputs.length; i++) {
            node.fluidInputs.add(
                i,
                new ObjectFloatImmutablePair<>(
                    r.mFluidInputs[i],
                    r.mFluidInputChances != null ? r.mFluidInputChances[i] : 1.f));
        }
        for (int i = 0; i < r.mFluidOutputs.length; i++) {
            node.fluidOutputs.add(
                i,
                new ObjectFloatImmutablePair<>(
                    r.mFluidOutputs[i],
                    r.mFluidOutputChances != null ? r.mFluidOutputChances[i] : 1.f));
        }
        return props;
    }
}
