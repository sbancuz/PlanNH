package com.sbancuz.plannh.data.provider;

import java.lang.reflect.Field;
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
import crazypants.enderio.nei.AlloySmelterRecipeHandler;
import crazypants.enderio.nei.AlloySmelterRecipeHandler.AlloySmelterRecipe;
import crazypants.enderio.nei.EnchanterRecipeHandler;
import crazypants.enderio.nei.SagMillRecipeHandler;
import crazypants.enderio.nei.SagMillRecipeHandler.MillRecipe;
import crazypants.enderio.nei.SliceAndSpliceRecipeHandler;
import crazypants.enderio.nei.SliceAndSpliceRecipeHandler.SliceAndSpliceRecipe;
import crazypants.enderio.nei.SoulBinderRecipeHandler;
import crazypants.enderio.nei.SoulBinderRecipeHandler.SoulBinderRecipeNEI;
import crazypants.enderio.nei.VatRecipeHandler;
import crazypants.enderio.nei.VatRecipeHandler.InnerVatRecipe;

public class EnderIOProvider implements PropertyProvider {

    // TODO: Make a PR to expose these constants
    // EnderIO machines run at 80 RF/t base (crazypants.enderio.machine.AbstractPowerConsumerEntity)
    private static final int RF_PER_TICK = 80;
    private static final String PROFILE_ID = "enderio";

    public static final RecipeProperty<Integer> EXPERIENCE = RecipeProperty.<Integer>builder("enderio.experience", 0)
        .build();

    @Nullable
    private static Field MILL_OUTPUT_CHANCE;

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(AlloySmelterRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(SagMillRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(VatRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(EnchanterRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(SliceAndSpliceRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(SoulBinderRecipeHandler.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder(PROFILE_ID, "EnderIO")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(
                    Effects.durationFromHandler()
                        .withCostPerT(CoFHCompat.RF_PER_T, (current, s, ctx) -> (long) RF_PER_TICK)
                        .computeTotal(CoFHCompat.RF_COST)
                        .applyParallelism())
                .build());

        Field f = null;
        try {
            f = MillRecipe.class.getDeclaredField("outputChance");
            f.setAccessible(true);
        } catch (final Exception ignored) {}
        MILL_OUTPUT_CHANCE = f;
    }

    @Override
    public boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        if (!(handler instanceof TemplateRecipeHandler trh)) return false;
        final String overlay = trh.getOverlayIdentifier();
        return overlay != null && (overlay.startsWith("EnderIO") || overlay.equals("EIOEnchanter"));
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        if (!(handler instanceof TemplateRecipeHandler trh)) return null;
        final String overlay = trh.getOverlayIdentifier();
        if (overlay != null && (overlay.startsWith("EnderIO") || overlay.equals("EIOEnchanter"))) return PROFILE_ID;
        return null;
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

        if (cached instanceof final AlloySmelterRecipe r) {
            applyEnergy(props, r.getEnergy());
        } else if (cached instanceof final MillRecipe r) {
            applyEnergy(props, r.getEnergy());
            applyMillChances(node, r);
        } else if (cached instanceof final SliceAndSpliceRecipe r) {
            applyEnergy(props, r.getEnergy());
        } else if (cached instanceof final SoulBinderRecipeNEI r) {
            applyEnergy(props, r.getEnergy());
            if (r.getExperience() > 0) props.put(EXPERIENCE, r.getExperience());
        } else if (cached instanceof final InnerVatRecipe r) {
            applyEnergy(props, r.getEnergy());
        }

        return props;
    }

    private static void applyEnergy(final Map<RecipeProperty<?>, Object> props, final int energy) {
        props.put(RecipePropertyAPI.DURATION_TICKS, energy / RF_PER_TICK);
        props.put(CoFHCompat.RF_COST, (long) energy);
    }

    private static void applyMillChances(final Node node, final MillRecipe r) {
        if (MILL_OUTPUT_CHANCE == null) return;
        try {
            final float[] chances = (float[]) MILL_OUTPUT_CHANCE.get(r);
            for (int i = 0; i < chances.length && i < node.getOutputs()
                .size(); i++) {
                node.getOutputs()
                    .get(i)
                    .setChance(chances[i]);
            }
        } catch (final Exception ignored) {}
    }
}
