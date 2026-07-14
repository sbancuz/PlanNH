package com.sbancuz.plannh.data.setting;

import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.data.MachineConfig;
import lombok.Getter;
import net.minecraft.util.StatCollector;

@Getter
public abstract class SettingDef<T> {

    private final String key;
    private final String label;
    private final T defaultValue;
    @Nullable
    private final BiFunction<T, MachineConfig, String> badgeFn;

    protected SettingDef(final String key, final T defaultValue, @Nullable final BiFunction<T, MachineConfig, String> badgeFn) {
        this.key = key;
        this.label = StatCollector.translateToLocal("plannh.settings." + key);
        this.defaultValue = defaultValue;
        this.badgeFn = badgeFn;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public String badge(@Nullable final Object val, final MachineConfig config) {
        if (badgeFn == null) return null;
        return badgeFn.apply((T) val, config);
    }
}
