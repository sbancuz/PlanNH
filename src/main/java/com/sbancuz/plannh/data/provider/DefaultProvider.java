package com.sbancuz.plannh.data.provider;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.setting.Settings;

import codechicken.nei.recipe.IRecipeHandler;

public final class DefaultProvider implements PropertyProvider {

    public static final DefaultProvider INSTANCE = new DefaultProvider();

    private DefaultProvider() {}

    @Override
    public void register() {
        MachineProfileRegistry.register(
            MachineProfile.builder("default", "Default")
                .setting(Settings.MACHINES)
                .setting(Settings.TICK_MODIFIER)
                .setting(Settings.DURATION_TICKS)
                .effect(DefaultProvider::noopEffect)
                .build());
    }

    @Override
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return "default";
    }

    public static EffectResult noopEffect(final MachineConfig config, final RecipeContext ctx) {
        int d = config.get(Settings.DURATION_TICKS);
        if (d <= 0) {
            final Object dur = ctx.properties().get(RecipePropertyAPI.DURATION_TICKS);
            d = dur instanceof final Number n ? n.intValue() : 0;
        }
        return new EffectResult(d, 0, config.get(Settings.MACHINES));
    }
}
