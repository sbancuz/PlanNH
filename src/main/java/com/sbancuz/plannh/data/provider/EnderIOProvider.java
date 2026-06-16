package com.sbancuz.plannh.data.provider;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.RecipeHandlerAccess;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;

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

    public static final RecipeProperty<Integer> RF_TOTAL = RecipeProperty.intProperty("rfTotal", "RF Total", 0);
    public static final RecipeProperty<Integer> EXPERIENCE = RecipeProperty.intProperty("experience", "Experience", 0);

    @Nullable
    private static Field MILL_OUTPUT_CHANCE;

    @Override
    @Nonnull
    public String getModId() {
        return Compat.ENDERIO.modid;
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(new AlloySmelterRecipeHandler().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new SagMillRecipeHandler().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new VatRecipeHandler().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new EnchanterRecipeHandler().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new SliceAndSpliceRecipeHandler().getOverlayIdentifier(), this);
        RecipePropertyAPI.registerExtractor(new SoulBinderRecipeHandler().getOverlayIdentifier(), this);

        RecipePropertyAPI.registerProperty(RF_TOTAL);
        RecipePropertyAPI.registerProperty(EXPERIENCE);

        MachineProfileRegistry.register(
            MachineProfile.builder("enderio", "EnderIO")
                .setting(Settings.MACHINES.def())
                .setting(Settings.RF_PER_TICK.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(EnderIOProvider::enderIOEffect)
                .build());

        Field f = null;
        try {
            f = MillRecipe.class.getDeclaredField("outputChance");
            f.setAccessible(true);
        } catch (final Exception ignored) {}
        MILL_OUTPUT_CHANCE = f;
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        if (!(handler instanceof TemplateRecipeHandler)) return null;
        final String overlay = handler.getOverlayIdentifier();
        if (overlay != null && (overlay.startsWith("EnderIO") || overlay.equals("EIOEnchanter"))) return "enderio";
        return null;
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler, final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof final TemplateRecipeHandler trh)) return props;

        final List<TemplateRecipeHandler.CachedRecipe> recipes = RecipeHandlerAccess.getArecipes(trh);
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return props;

        final TemplateRecipeHandler.CachedRecipe cached = recipes.get(recipeIndex);

        if (cached instanceof final AlloySmelterRecipe r) {
            props.put(RF_TOTAL, r.getEnergy());
        } else if (cached instanceof final MillRecipe r) {
            props.put(RF_TOTAL, r.getEnergy());
            if (MILL_OUTPUT_CHANCE != null) {
                try {
                    final float[] chances = (float[]) MILL_OUTPUT_CHANCE.get(r);
                    for (int i = 0; i < chances.length && i < node.outputs.size(); i++) {
                        final Port port = node.outputs.get(i);
                        if (port.getType() == RecipePropertyAPI.ITEM) {
                            port.setChance(chances[i]);
                        }
                    }
                } catch (final Exception ignored) {}
            }
        } else if (cached instanceof final SliceAndSpliceRecipe r) {
            props.put(RF_TOTAL, r.getEnergy());
        } else if (cached instanceof final SoulBinderRecipeNEI r) {
            props.put(RF_TOTAL, r.getEnergy());
            if (r.getExperience() > 0) props.put(EXPERIENCE, r.getExperience());
        } else if (cached instanceof final InnerVatRecipe r) {
            props.put(RF_TOTAL, r.getEnergy());
        }

        return props;
    }

    @Nonnull
    private static MachineProfile.EffectResult enderIOEffect(final Map<String, Object> s,
        final MachineProfile.RecipeContext ctx) {
        final int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        final int rate = MachineProfile.getInt(s, Settings.RF_PER_TICK.key(), 80);
        final Integer totalEnergy = ctx.get(EnderIOProvider.RF_TOTAL);
        int duration = ctx.recipeDuration();
        if (duration <= 0 && rate > 0 && totalEnergy != null && totalEnergy > 0) {
            duration = Math.max(1, totalEnergy / rate);
        }
        final long consumptionEUt = duration > 0 && totalEnergy != null ? totalEnergy / duration : 0;
        return new MachineProfile.EffectResult(duration, consumptionEUt, machines);
    }
}
