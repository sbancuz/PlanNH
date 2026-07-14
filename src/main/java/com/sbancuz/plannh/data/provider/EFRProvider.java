package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.setting.Settings;

import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.IRecipeHandler;

public class EFRProvider implements PropertyProvider {

    public static final String SMOKER_OVERLAY = "etfuturum.smoker";
    public static final String BLAST_FURNACE_OVERLAY = "etfuturum.blastfurnace";

    public static void registerHandlers(PropertyProvider provider) {
        RecipePropertyAPI.registerExtractor(SMOKER_OVERLAY, provider);
        RecipePropertyAPI.registerExtractor(BLAST_FURNACE_OVERLAY, provider);
    }

    @Override
    public void register() {
        registerHandlers(this);
        MachineProfileRegistry.register(
            MachineProfile.builder("etfuturum", "Et Futurum Requiem")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(EFRProvider::efrEffect)
                .build());
    }

    @Nonnull
    private static MachineProfile.EffectResult efrEffect(final Map<String, Object> s,
        final MachineProfile.RecipeContext ctx) {
        final int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        return new MachineProfile.EffectResult(ctx.recipeDuration(), 0, machines);
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        if (!(handler instanceof FurnaceRecipeHandler fh)) return null;
        final String overlay = fh.getOverlayIdentifier();
        if (!SMOKER_OVERLAY.equals(overlay) && !BLAST_FURNACE_OVERLAY.equals(overlay)) return null;
        return "etfuturum";
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>();
        if (!(handler instanceof FurnaceRecipeHandler fh)) return props;
        final String overlay = fh.getOverlayIdentifier();
        if (!SMOKER_OVERLAY.equals(overlay) && !BLAST_FURNACE_OVERLAY.equals(overlay)) return props;
        props.put(RecipePropertyAPI.DURATION_TICKS, 100);
        return props;
    }
}
