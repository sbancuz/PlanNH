package com.sbancuz.plannh.data.setting;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import org.jetbrains.annotations.Nullable;

import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeContext;

import lombok.Getter;

@Getter
public class StringSettingDef extends SettingDef<String> {

    private final List<String> options;

    public StringSettingDef(String key, String defaultValue, List<String> options,
        @Nullable BiFunction<String, MachineConfig, String> badgeFn,
        BiPredicate<RecipeContext, Map<String, Object>> visibility) {
        super(key, defaultValue, badgeFn, visibility);
        this.options = options;
    }

    public StringSettingDef(String key, String defaultValue, List<String> options,
        @Nullable BiFunction<String, MachineConfig, String> badgeFn) {
        this(key, defaultValue, options, badgeFn, (_, _) -> true);
    }
}
