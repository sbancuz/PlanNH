package com.sbancuz.plannh.data.provider;

import java.util.Map;

import com.sbancuz.plannh.api.RecipePropertyAPI;
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
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .setting(Settings.DURATION_TICKS.def())
                .effect(DefaultProvider::noopEffect)
                .build());
    }

    @Override
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return "default";
    }

    public static EffectResult noopEffect(final Map<String, Object> s, final RecipeContext ctx) {
        int d = MachineProfile.getInt(s, Settings.DURATION_TICKS.key(), 0);
        if (d <= 0) {
            final Object dur = ctx.properties().get(RecipePropertyAPI.DURATION_TICKS);
            d = dur instanceof final Number n ? n.intValue() : 0;
        }
        return new EffectResult(d, 0, MachineProfile.getInt(s, Settings.MACHINES.key(), 1));
    }
}
