package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.setting.Settings;

import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.IRecipeHandler;

// TODO: Make a PR to expose these constants
// Smoker and Blast Furnace cook at half the speed of a vanilla furnace (200/2 = 100 ticks)
// Matches vanilla Minecraft 1.13+ behavior (smoker/blast furnace cook in 100 ticks)
public class EFRProvider implements PropertyProvider {

    public static final String SMOKER_OVERLAY = "etfuturum.smoker";
    public static final String BLAST_FURNACE_OVERLAY = "etfuturum.blastfurnace";

    private static final int COOK_TICKS = 100;

    public static void registerHandlers(PropertyProvider provider) {
        RecipePropertyAPI.registerExtractor(FurnaceRecipeHandler.class, provider);
    }

    @Override
    public void register() {
        registerHandlers(this);
        MachineProfileRegistry.register(
            MachineProfile.builder("etfuturum", "Et Futurum Requiem")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(
                    Effects.durationFromHandler()
                        .applyParallelism())
                .build());
    }

    @Override
    public boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        if (!(handler instanceof FurnaceRecipeHandler fh)) return false;
        final String overlay = fh.getOverlayIdentifier();
        return SMOKER_OVERLAY.equals(overlay) || BLAST_FURNACE_OVERLAY.equals(overlay);
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        if (!(handler instanceof FurnaceRecipeHandler fh)) return null;
        return switch (fh.getOverlayIdentifier()) {
            case SMOKER_OVERLAY, BLAST_FURNACE_OVERLAY -> "etfuturum";
            default -> null;
        };
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>(
            PropertyProvider.super.extract(node, handler, recipeIndex));
        if (!(handler instanceof FurnaceRecipeHandler fh)) return props;
        final String overlay = fh.getOverlayIdentifier();
        if (!SMOKER_OVERLAY.equals(overlay) && !BLAST_FURNACE_OVERLAY.equals(overlay)) return props;
        props.put(RecipePropertyAPI.DURATION_TICKS, COOK_TICKS);
        return props;
    }
}
