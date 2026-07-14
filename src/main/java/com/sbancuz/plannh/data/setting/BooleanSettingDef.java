package com.sbancuz.plannh.data.setting;

import com.sbancuz.plannh.data.MachineConfig;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public class BooleanSettingDef extends SettingDef<Boolean>{
    public BooleanSettingDef(String key, Boolean defaultValue, @Nullable BiFunction<Boolean, MachineConfig, String> badgeFn) {
        super(key, defaultValue, badgeFn);
    }
}
