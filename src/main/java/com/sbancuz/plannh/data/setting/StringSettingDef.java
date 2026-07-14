package com.sbancuz.plannh.data.setting;

import java.util.List;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import com.sbancuz.plannh.data.MachineConfig;

import lombok.Getter;

@Getter
public class StringSettingDef extends SettingDef<String> {

    private final List<String> options;

    public StringSettingDef(String key, String defaultValue, List<String> options,
        @Nullable BiFunction<String, MachineConfig, String> badgeFn) {
        super(key, defaultValue, badgeFn);
        this.options = options;
    }
}
