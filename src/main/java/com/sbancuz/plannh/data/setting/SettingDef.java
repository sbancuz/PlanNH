package com.sbancuz.plannh.data.setting;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeContext;

import lombok.Getter;

@Getter
public abstract class SettingDef<T> {

    private final String key;
    private final String label;
    private final T defaultValue;
    @Nullable
    private final BiFunction<T, MachineConfig, String> badgeFn;
    private BiPredicate<RecipeContext, Map<String, Object>> visibility;

    protected SettingDef(final String key, final T defaultValue,
        @Nullable final BiFunction<T, MachineConfig, String> badgeFn,
        BiPredicate<RecipeContext, Map<String, Object>> visibility) {
        this.key = key;
        this.label = StatCollector.translateToLocal("plannh.settings." + key);
        this.defaultValue = defaultValue;
        this.badgeFn = badgeFn;
        this.visibility = visibility;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public String badge(@Nullable final Object val, final MachineConfig config) {
        if (badgeFn == null) return null;
        return badgeFn.apply((T) val, config);
    }

    public boolean isVisible(final RecipeContext ctx, final Map<String, Object> settings) {
        return visibility.test(ctx, settings);
    }

    public SettingDef<T> withVisibility(final BiPredicate<RecipeContext, Map<String, Object>> visibility) {
        this.visibility = visibility;
        return this;
    }
}
