package com.sbancuz.plannh.data.setting;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import org.jetbrains.annotations.Nullable;

import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeContext;

public class BooleanSettingDef extends SettingDef<Boolean> {

    protected BooleanSettingDef(String key, Boolean defaultValue,
        @Nullable BiFunction<Boolean, MachineConfig, String> badgeFn,
        BiPredicate<RecipeContext, Map<String, Object>> visibility) {
        super(key, defaultValue, badgeFn, visibility);
    }

    public BooleanSettingDef(String key, Boolean defaultValue,
        @Nullable BiFunction<Boolean, MachineConfig, String> badgeFn) {
        this(key, defaultValue, badgeFn, (_, _) -> true);
    }
}
