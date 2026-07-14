package com.sbancuz.plannh.data.setting;

import java.util.function.BiFunction;

import javax.annotation.Nullable;

import com.sbancuz.plannh.data.MachineConfig;

import lombok.Getter;

@Getter
public class IntegerSettingDef extends SettingDef<Integer> {

    public final int min;
    public final int max;

    public IntegerSettingDef(final String key, final int def, final int min, final int max,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn) {
        super(key, def, badgeFn);
        this.min = min;
        this.max = max;
    }

    public IntegerSettingDef(final String key, final int def, final int min, final int max) {
        this(key, def, min, max, null);
    }
}
