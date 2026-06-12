package com.sbancuz.plannh.data;

import java.util.List;
import java.util.function.BiFunction;

public class SettingDef<T> {

    public final String key;
    public final String label;
    public final Class<T> type;
    public final T defaultValue;
    public final int minInt;
    public final int maxInt;
    public final List<String> options;
    private final BiFunction<T, MachineConfig, String> badgeFn;

    private SettingDef(final String key, final String label, final Class<T> type, final T defaultValue,
        final int minInt, final int maxInt, final List<String> options,
        final BiFunction<T, MachineConfig, String> badgeFn) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.defaultValue = defaultValue;
        this.minInt = minInt;
        this.maxInt = maxInt;
        this.options = options;
        this.badgeFn = badgeFn;
    }

    public static SettingDef<Integer> intDef(final String key, final String label, final int def, final int min,
        final int max) {
        return intDef(key, label, def, min, max, null);
    }

    public static SettingDef<Integer> intDef(final String key, final String label, final int def, final int min,
        final int max, final BiFunction<Integer, MachineConfig, String> badgeFn) {
        return new SettingDef<>(key, label, Integer.class, def, min, max, null, badgeFn);
    }

    public static SettingDef<Boolean> boolDef(final String key, final String label, final boolean def,
        final BiFunction<Boolean, MachineConfig, String> badgeFn) {
        return new SettingDef<>(key, label, Boolean.class, def, 0, 0, null, badgeFn);
    }

    public static SettingDef<String> enumDef(final String key, final String label, final String def,
        final List<String> options, final BiFunction<String, MachineConfig, String> badgeFn) {
        return new SettingDef<>(key, label, String.class, def, 0, 0, options, badgeFn);
    }

    public boolean hasOptions() {
        return options != null && !options.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public String badge(final Object val, final MachineConfig config) {
        if (badgeFn == null) return null;
        return badgeFn.apply((T) val, config);
    }
}
