package com.sbancuz.plannh.data;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;
import java.util.function.UnaryOperator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

public class SettingDef<T> {

    private static final BiPredicate<RecipeContext, Map<String, Object>> ALWAYS = (ctx, s) -> true;

    public final String key;
    public final String label;
    public final Class<T> type;
    public final T defaultValue;
    public final int minInt;
    public final int maxInt;
    @Nullable
    public final List<String> options;
    @Nullable
    private final Function<RecipeContext, List<String>> optionsFn;
    @Nullable
    private final UnaryOperator<String> displayFn;
    @Nullable
    private final BiFunction<T, MachineConfig, String> badgeFn;
    private final BiPredicate<RecipeContext, Map<String, Object>> visibility;
    @Nullable
    private final ToIntBiFunction<RecipeContext, Map<String, Object>> autoValueFn;
    /** A ceiling the machine sets, for rows whose maximum is not also their automatic value. */
    @Nullable
    private final ToIntBiFunction<RecipeContext, Map<String, Object>> maxFn;

    private SettingDef(final String key, final Class<T> type, final T defaultValue, final int minInt, final int maxInt,
        @Nullable final List<String> options, @Nullable final Function<RecipeContext, List<String>> optionsFn,
        @Nullable final UnaryOperator<String> displayFn, @Nullable final BiFunction<T, MachineConfig, String> badgeFn,
        final BiPredicate<RecipeContext, Map<String, Object>> visibility,
        @Nullable final ToIntBiFunction<RecipeContext, Map<String, Object>> autoValueFn,
        @Nullable final ToIntBiFunction<RecipeContext, Map<String, Object>> maxFn) {
        this.key = key;
        this.label = StatCollector.translateToLocal("plannh.settings." + key);
        this.type = type;
        this.defaultValue = defaultValue;
        this.minInt = minInt;
        this.maxInt = maxInt;
        this.options = options;
        this.optionsFn = optionsFn;
        this.displayFn = displayFn;
        this.badgeFn = badgeFn;
        this.visibility = visibility;
        this.autoValueFn = autoValueFn;
        this.maxFn = maxFn;
    }

    @Nonnull
    public static SettingDef<Integer> intDef(final String key, final int def, final int min, final int max) {
        return intDef(key, def, min, max, null);
    }

    /**
     * An integer row whose ceiling the selected machine decides. Distinct from {@link #autoIntDef}:
     * there the machine's number is also what an untouched row shows, here the row still starts at its
     * own default and only the top of the range moves.
     */
    @Nonnull
    public static SettingDef<Integer> intDef(final String key, final int def, final int min,
        final ToIntBiFunction<RecipeContext, Map<String, Object>> maxFn) {
        return new SettingDef<>(key, Integer.class, def, min, min, null, null, null, null, ALWAYS, null, maxFn);
    }

    @Nonnull
    public static SettingDef<Integer> intDef(final String key, final int def, final int min, final int max,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn) {
        return new SettingDef<>(key, Integer.class, def, min, max, null, null, null, badgeFn, ALWAYS, null, null);
    }

    @Nonnull
    public static SettingDef<Boolean> boolDef(final String key, final boolean def,
        final BiFunction<Boolean, MachineConfig, String> badgeFn) {
        return new SettingDef<>(key, Boolean.class, def, 0, 0, null, null, null, badgeFn, ALWAYS, null, null);
    }

    @Nonnull
    public static SettingDef<String> enumDef(final String key, final String def, final List<String> options,
        final BiFunction<String, MachineConfig, String> badgeFn) {
        return new SettingDef<>(key, String.class, def, 0, 0, options, null, null, badgeFn, ALWAYS, null, null);
    }

    /**
     * An enum whose choices depend on the recipe - the machine picker, whose options are the GT
     * machines that can run this node's recipemap. {@code optionsFn} is stored, never called, at
     * construction: the profile is a static singleton built before any node exists.
     *
     * <p>
     * The list must come back best-choice-first. The widget treats index 0 as the value an unset
     * setting resolves to, which is what lets a node auto-select with nothing serialized.
     *
     * @param displayFn maps a stored id to what the row shows, so the saved value can stay stable
     *                  and locale-independent while the row reads as a machine name.
     */
    @Nonnull
    public static SettingDef<String> dynamicEnumDef(final String key, final String def,
        final Function<RecipeContext, List<String>> optionsFn, final UnaryOperator<String> displayFn,
        @Nullable final BiFunction<String, MachineConfig, String> badgeFn) {
        return new SettingDef<>(key, String.class, def, 0, 0, null, optionsFn, displayFn, badgeFn, ALWAYS, null, null);
    }

    /**
     * A setting whose useful value comes from the machine rather than from a fixed default - the
     * parallel count, the overclock factors, the coil heat. Nothing stored means "whatever the
     * machine does", so the row shows a real number and the node never freezes a value that goes
     * stale when its machine or structure changes.
     */
    @Nonnull
    public static SettingDef<Integer> autoIntDef(final String key, final int min, final int max,
        final ToIntBiFunction<RecipeContext, Map<String, Object>> autoValueFn,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn) {
        return new SettingDef<>(key, Integer.class, 0, min, max, null, null, null, badgeFn, ALWAYS, autoValueFn, null);
    }

    /**
     * An auto row the machine also caps, for the one case where what it does and the most it can do
     * are the same number. Everywhere else the automatic value is a starting point and capping the row
     * at it would leave the row unable to move up from what it already shows.
     */
    @Nonnull
    public static SettingDef<Integer> autoIntDefCapped(final String key, final int min, final int max,
        final ToIntBiFunction<RecipeContext, Map<String, Object>> autoValueFn,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn) {
        return new SettingDef<>(
            key,
            Integer.class,
            0,
            min,
            max,
            null,
            null,
            null,
            badgeFn,
            ALWAYS,
            autoValueFn,
            autoValueFn);
    }

    /**
     * The boolean form. Shares {@code autoValueFn} rather than adding a parallel field: a flag is
     * just an int the caller reads as zero or not.
     */
    @Nonnull
    public static SettingDef<Boolean> autoBoolDef(final String key,
        final ToIntBiFunction<RecipeContext, Map<String, Object>> autoValueFn,
        @Nullable final BiFunction<Boolean, MachineConfig, String> badgeFn) {
        return new SettingDef<>(key, Boolean.class, false, 0, 0, null, null, null, badgeFn, ALWAYS, autoValueFn, null);
    }

    public boolean isAuto() {
        return autoValueFn != null;
    }

    /**
     * What the row shows and the maths uses: the stored override, or the machine's own value.
     * Presence is the whole test - a stored zero is a deliberate zero, which is exactly what the
     * sentinel this replaced could not say.
     */
    public int effectiveInt(final RecipeContext ctx, final Map<String, Object> settings) {
        if (settings.containsKey(key) || autoValueFn == null) return MachineProfile.getInt(settings, key, 0);
        return autoValueFn.applyAsInt(ctx, settings);
    }

    public boolean effectiveBool(final RecipeContext ctx, final Map<String, Object> settings) {
        if (settings.containsKey(key) || autoValueFn == null) return MachineProfile.getBool(settings, key, false);
        return autoValueFn.applyAsInt(ctx, settings) != 0;
    }

    /**
     * Stepping up stops at what the machine can actually do, where the machine has a say. Only a row
     * given a {@code maxFn} has one: an automatic value is what the machine <em>does</em>, which is a
     * ceiling for a parallel count and a starting point for everything else, so it is not assumed to
     * be one.
     */
    public int effectiveMax(final RecipeContext ctx, final Map<String, Object> settings) {
        return maxFn == null ? maxInt : Math.max(minInt, maxFn.applyAsInt(ctx, settings));
    }

    public boolean hasOptions() {
        return optionsFn != null || (options != null && !options.isEmpty());
    }

    /** The choices valid for this recipe. Empty means the row has nothing to offer and is skipped. */
    @Nonnull
    public List<String> options(final RecipeContext ctx) {
        if (optionsFn != null) return optionsFn.apply(ctx);
        return options != null ? options : List.of();
    }

    /** What the row renders for a stored value; identity unless the setting maps ids to names. */
    @Nonnull
    public String display(final String value) {
        return displayFn == null ? value : displayFn.apply(value);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public String badge(@Nullable final Object val, final MachineConfig config) {
        if (badgeFn == null) return null;
        return badgeFn.apply((T) val, config);
    }

    public boolean isVisible(final RecipeContext ctx, final Map<String, Object> settings) {
        return visibility.test(ctx, settings);
    }

    /**
     * Returns a copy, so the shared singleton handed out by {@link Settings#def} stays unconditioned
     * and one profile's gating cannot leak into another's.
     */
    @Nonnull
    public SettingDef<T> withVisibility(final BiPredicate<RecipeContext, Map<String, Object>> condition) {
        return new SettingDef<>(
            key,
            type,
            defaultValue,
            minInt,
            maxInt,
            options,
            optionsFn,
            displayFn,
            badgeFn,
            condition,
            autoValueFn,
            maxFn);
    }
}
