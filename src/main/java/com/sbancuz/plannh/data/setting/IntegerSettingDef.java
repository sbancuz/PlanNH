package com.sbancuz.plannh.data.setting;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import javax.annotation.Nullable;

import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeContext;

import lombok.Getter;

@Getter
public class IntegerSettingDef extends SettingDef<Integer> {

    public final int min;
    public final int max;

    public IntegerSettingDef(final String key, final int def, final int min, final int max,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn,
        BiPredicate<RecipeContext, Map<String, Object>> visibility) {
        super(key, def, badgeFn, visibility);
        this.min = min;
        this.max = max;
    }

    public IntegerSettingDef(final String key, final int def, final int min, final int max,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn) {
        this(key, def, min, max, badgeFn, (_, _) -> true);
    }

    public IntegerSettingDef(final String key, final int def, final int min, final int max) {
        this(key, def, min, max, null);
    }
}
