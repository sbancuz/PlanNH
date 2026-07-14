package com.sbancuz.plannh.data.setting;

import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import com.sbancuz.plannh.data.MachineConfig;

public class BooleanSettingDef extends SettingDef<Boolean> {

    public BooleanSettingDef(String key, Boolean defaultValue,
        @Nullable BiFunction<Boolean, MachineConfig, String> badgeFn) {
        super(key, defaultValue, badgeFn);
    }
}
