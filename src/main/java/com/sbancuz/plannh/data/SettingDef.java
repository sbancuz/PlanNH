package com.sbancuz.plannh.data;

import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

public class SettingDef<T> {

    public final String key;
    public final String label;
    public final Class<T> type;
    public final T defaultValue;
    public final int minInt;
    public final int maxInt;
    @Nullable
    public final List<String> options;
    @Nullable
    private final BiFunction<T, MachineConfig, String> badgeFn;

    private SettingDef(final String key, final Class<T> type, final T defaultValue, final int minInt, final int maxInt,
        @Nullable final List<String> options, @Nullable final BiFunction<T, MachineConfig, String> badgeFn) {
        this.key = key;
        this.label = StatCollector.translateToLocal("plannh.settings." + key);
        this.type = type;
        this.defaultValue = defaultValue;
        this.minInt = minInt;
        this.maxInt = maxInt;
        this.options = options;
        this.badgeFn = badgeFn;
    }

    @Nonnull
    public static SettingDef<Integer> intDef(final String key, final int def, final int min, final int max) {
        return intDef(key, def, min, max, null);
    }

    @Nonnull
    public static SettingDef<Integer> intDef(final String key, final int def, final int min, final int max,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn) {
        return new SettingDef<>(key, Integer.class, def, min, max, null, badgeFn);
    }

    @Nonnull
    public static SettingDef<Boolean> boolDef(final String key, final boolean def,
        final BiFunction<Boolean, MachineConfig, String> badgeFn) {
        return new SettingDef<>(key, Boolean.class, def, 0, 0, null, badgeFn);
    }

    @Nonnull
    public static SettingDef<String> enumDef(final String key, final String def, final List<String> options,
        final BiFunction<String, MachineConfig, String> badgeFn) {
        return new SettingDef<>(key, String.class, def, 0, 0, options, badgeFn);
    }

    public boolean hasOptions() {
        return options != null && !options.isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public String badge(@Nullable final Object val, final MachineConfig config) {
        if (badgeFn == null) return null;
        return badgeFn.apply((T) val, config);
    }
}
