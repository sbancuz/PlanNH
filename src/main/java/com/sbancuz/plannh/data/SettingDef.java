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

import lombok.AccessLevel;
import lombok.Builder;

public class SettingDef<T> {

    private static final BiPredicate<RecipeContext, Map<String, Object>> ALWAYS = (ctx, s) -> true;

    public final String key;
    public final String label;
    public final Class<T> type;
    public final T defaultValue;
    public final int minInt;
    public final int maxInt;
    @Nullable
    private final Function<RecipeContext, List<String>> optionsFn;
    /** The value of an unset enum row. Null means the first option. */
    @Nullable
    private final Function<RecipeContext, String> defaultFn;
    @Nullable
    private final UnaryOperator<String> displayFn;
    @Nullable
    private final BiFunction<T, MachineConfig, String> badgeFn;
    private final BiPredicate<RecipeContext, Map<String, Object>> visibility;
    @Nullable
    private final ToIntBiFunction<RecipeContext, Map<String, Object>> autoValueFn;
    /** A floor the machine sets, for rows whose range belongs to the selected machine. */
    @Nullable
    private final ToIntBiFunction<RecipeContext, Map<String, Object>> minFn;
    /** A ceiling the machine sets, for rows whose maximum is not also their automatic value. */
    @Nullable
    private final ToIntBiFunction<RecipeContext, Map<String, Object>> maxFn;
    /**
     * The value that means the machine did nothing here, in this row's units. Passed explicitly, not
     * inferred from {@link #defaultValue} (the unset marker for an auto row) or from the {@code Settings}
     * constant a provider borrowed the key from (whose scale can differ).
     */
    @Nullable
    private final Integer neutral;

    @Builder(toBuilder = true, access = AccessLevel.PRIVATE)
    private SettingDef(final String key, @Nullable final String label, final Class<T> type, final T defaultValue,
        final int minInt, final int maxInt, @Nullable final Function<RecipeContext, List<String>> optionsFn,
        @Nullable final Function<RecipeContext, String> defaultFn, @Nullable final UnaryOperator<String> displayFn,
        @Nullable final BiFunction<T, MachineConfig, String> badgeFn,
        @Nullable final BiPredicate<RecipeContext, Map<String, Object>> visibility,
        @Nullable final ToIntBiFunction<RecipeContext, Map<String, Object>> autoValueFn,
        @Nullable final ToIntBiFunction<RecipeContext, Map<String, Object>> minFn,
        @Nullable final ToIntBiFunction<RecipeContext, Map<String, Object>> maxFn, @Nullable final Integer neutral) {
        this.neutral = neutral;
        this.key = key;
        this.label = label != null ? label : StatCollector.translateToLocal("plannh.settings." + key);
        this.type = type;
        this.defaultValue = defaultValue;
        this.minInt = minInt;
        this.maxInt = maxInt;
        this.optionsFn = optionsFn;
        this.defaultFn = defaultFn;
        this.displayFn = displayFn;
        this.badgeFn = badgeFn;
        this.visibility = visibility == null ? ALWAYS : visibility;
        this.autoValueFn = autoValueFn;
        this.minFn = minFn;
        this.maxFn = maxFn;
    }

    /** Whether {@code value} means the machine did nothing, in this row's units. */
    public boolean isNeutral(final int value) {
        return neutral != null && neutral == value;
    }

    @Nonnull
    public static SettingDef<Integer> intDef(final String key, final int def, final int min, final int max) {
        return intDef(key, def, min, max, null);
    }

    /**
     * An integer row whose ceiling comes from the selected machine. Unlike {@link #autoIntDef}, an
     * untouched row starts at {@code def}, not at the machine's number, and only the top of the range
     * moves.
     */
    @Nonnull
    public static SettingDef<Integer> intDef(final String key, final int def, final int min,
        final ToIntBiFunction<RecipeContext, Map<String, Object>> maxFn) {
        return SettingDef.<Integer>builder()
            .key(key)
            .type(Integer.class)
            .defaultValue(def)
            .minInt(min)
            .maxInt(min)
            .maxFn(maxFn)
            .build();
    }

    @Nonnull
    public static SettingDef<Integer> intDef(final String key, final int def, final int min, final int max,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn) {
        return SettingDef.<Integer>builder()
            .key(key)
            .type(Integer.class)
            .defaultValue(def)
            .minInt(min)
            .maxInt(max)
            .badgeFn(badgeFn)
            .build();
    }

    @Nonnull
    public static SettingDef<Boolean> boolDef(final String key, final boolean def,
        final BiFunction<Boolean, MachineConfig, String> badgeFn) {
        return SettingDef.<Boolean>builder()
            .key(key)
            .type(Boolean.class)
            .defaultValue(def)
            .badgeFn(badgeFn)
            .build();
    }

    /**
     * A fixed list of choices. An empty list leaves {@code optionsFn} null, so {@link #hasOptions}
     * works without a recipe.
     */
    @Nonnull
    public static SettingDef<String> enumDef(final String key, final String def, final List<String> options,
        final BiFunction<String, MachineConfig, String> badgeFn) {
        return SettingDef.<String>builder()
            .key(key)
            .type(String.class)
            .defaultValue(def)
            .optionsFn(options.isEmpty() ? null : ctx -> options)
            .badgeFn(badgeFn)
            .build();
    }

    /**
     * A setting whose choices come from the mod that owns the machine. PlanNH sets only the key. The
     * provider attaches the real def when it builds its profile, as {@code GTProvider} does for the
     * voltage and coil rows.
     *
     * <p>
     * No range or options are set here. A placeholder would be a second source for what the mod allows,
     * and would go wrong when the mod adds a tier, the failure this indirection exists to prevent. Until
     * a provider supplies a def, {@link #hasOptions} returns false and the row has no choices.
     */
    @Nonnull
    public static SettingDef<String> providedDef(final String key) {
        return SettingDef.<String>builder()
            .key(key)
            .type(String.class)
            .defaultValue("")
            .build();
    }

    /**
     * An enum whose choices depend on the recipe, such as the machine picker, whose options are the
     * machines that can run this node's recipe. {@code optionsFn} is stored, not called, at construction:
     * the profile is a static singleton built before any node exists.
     *
     * <p>
     * The list must be ordered best choice first. The widget uses index 0 as the value of an unset
     * setting, so a node auto-selects with nothing serialized.
     *
     * @param displayFn maps a stored id to the label the row draws, so the saved value stays stable and
     *                  locale-independent while the row prints a machine name.
     */
    @Nonnull
    public static SettingDef<String> dynamicEnumDef(final String key, final String def,
        final Function<RecipeContext, List<String>> optionsFn, final UnaryOperator<String> displayFn,
        @Nullable final BiFunction<String, MachineConfig, String> badgeFn) {
        return SettingDef.<String>builder()
            .key(key)
            .type(String.class)
            .defaultValue(def)
            .optionsFn(optionsFn)
            .displayFn(displayFn)
            .badgeFn(badgeFn)
            .build();
    }

    /**
     * A setting whose value comes from the machine, not a fixed default: the parallel count, the
     * overclock factors, the coil heat. Nothing stored means "whatever the machine does", so the row
     * draws a real number and the node stores no value that goes stale when its machine or structure
     * changes.
     */
    @Nonnull
    public static SettingDef<Integer> autoIntDef(final String key, final int min, final int max,
        final ToIntBiFunction<RecipeContext, Map<String, Object>> autoValueFn,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn) {
        return autoIntDef(key, min, max, null, autoValueFn, badgeFn);
    }

    /** As above, with {@code neutral} as the value that means the machine did nothing. */
    @Nonnull
    public static SettingDef<Integer> autoIntDef(final String key, final int min, final int max,
        @Nullable final Integer neutral, final ToIntBiFunction<RecipeContext, Map<String, Object>> autoValueFn,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn) {
        return SettingDef.<Integer>builder()
            .key(key)
            .type(Integer.class)
            .defaultValue(0)
            .minInt(min)
            .maxInt(max)
            .badgeFn(badgeFn)
            .autoValueFn(autoValueFn)
            .neutral(neutral)
            .build();
    }

    /**
     * An auto row the machine also caps, for a setting whose automatic value is also its maximum.
     * Elsewhere the automatic value is a starting point, and capping the row there would stop it from
     * stepping above its starting value.
     */
    @Nonnull
    public static SettingDef<Integer> autoIntDefCapped(final String key, final int min, final int max,
        @Nullable final Integer neutral, final ToIntBiFunction<RecipeContext, Map<String, Object>> autoValueFn,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn) {
        return SettingDef.<Integer>builder()
            .key(key)
            .type(Integer.class)
            .defaultValue(0)
            .minInt(min)
            .maxInt(max)
            .badgeFn(badgeFn)
            .autoValueFn(autoValueFn)
            .maxFn(autoValueFn)
            .neutral(neutral)
            .build();
    }

    /**
     * The boolean form. Reuses {@code autoValueFn}, not a separate boolean field: a flag is an int read
     * as zero or nonzero.
     */
    @Nonnull
    public static SettingDef<Boolean> autoBoolDef(final String key,
        final ToIntBiFunction<RecipeContext, Map<String, Object>> autoValueFn,
        @Nullable final BiFunction<Boolean, MachineConfig, String> badgeFn) {
        return SettingDef.<Boolean>builder()
            .key(key)
            .type(Boolean.class)
            .defaultValue(false)
            .badgeFn(badgeFn)
            .autoValueFn(autoValueFn)
            .build();
    }

    public boolean isAuto() {
        return autoValueFn != null;
    }

    /**
     * The value the row draws and the maths uses: the stored override, or the machine's value. Only key
     * presence counts: a stored zero is a user-chosen zero.
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
     * Stepping up stops at the machine's maximum, where one exists. Only a row with a {@code maxFn} has
     * one. The automatic value is the machine's working value: for a parallel count also the ceiling,
     * for everything else a starting point, so it is not used as a maximum.
     */
    public int effectiveMax(final RecipeContext ctx, final Map<String, Object> settings) {
        return maxFn == null ? maxInt : Math.max(effectiveMin(ctx, settings), maxFn.applyAsInt(ctx, settings));
    }

    public int effectiveMin(final RecipeContext ctx, final Map<String, Object> settings) {
        return minFn == null ? minInt : minFn.applyAsInt(ctx, settings);
    }

    public boolean hasOptions() {
        return optionsFn != null;
    }

    /** The choices valid for this recipe. Empty means the row is skipped. */
    @Nonnull
    public List<String> options(final RecipeContext ctx) {
        return optionsFn == null ? List.of() : optionsFn.apply(ctx);
    }

    /**
     * The value of an unset enum row. Nothing stored is the normal state, since the sparse settings map
     * stores only what the user chose. So the row draws this value and the maths reads it, and it must be
     * computed here, not per call site. Falls back to the first option, which is why an options list must
     * be ordered best choice first.
     */
    @Nonnull
    public String defaultOption(final RecipeContext ctx) {
        return defaultOption(ctx, options(ctx));
    }

    /** For a caller that already built the list. {@code options} rebuilds it on every call. */
    @Nonnull
    public String defaultOption(final RecipeContext ctx, final List<String> choices) {
        if (defaultFn != null) {
            final String preferred = defaultFn.apply(ctx);
            if (choices.contains(preferred)) return preferred;
        }
        return choices.isEmpty() ? "" : choices.getFirst();
    }

    /** The label the row draws for a stored value. Identity unless the setting maps ids to names. */
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
     * Renders the row's value as something other than the stored number, such as a pipe casing tier as
     * the casing a player places. The stored value stays a plain integer, since every machine reads the
     * tier as that integer. Only the row prints the build step.
     */
    @Nonnull
    public SettingDef<T> withDisplay(final UnaryOperator<String> display) {
        return toBuilder().displayFn(display)
            .build();
    }

    /**
     * Sets the value of an unset row, for a setting whose starting value depends on the chart, such as
     * the coil a chart is planned with. Nothing is stored until the user edits the row, so this follows
     * the chart and is not copied into every node.
     */
    @Nonnull
    public SettingDef<T> withDefault(final Function<RecipeContext, String> defaultOption) {
        return toBuilder().defaultFn(defaultOption)
            .build();
    }

    /**
     * A copy with a label and range from outside PlanNH: a GregTech structure row takes both from its
     * machine.
     */
    @Nonnull
    public SettingDef<T> withLabelAndRange(final String newLabel,
        final ToIntBiFunction<RecipeContext, Map<String, Object>> newMinFn,
        final ToIntBiFunction<RecipeContext, Map<String, Object>> newMaxFn) {
        return toBuilder().label(newLabel)
            .minFn(newMinFn)
            .maxFn(newMaxFn)
            .build();
    }

    /**
     * Returns a copy, so the shared singleton from {@link Settings#def} stays ungated and one profile's
     * gating can't leak into another's.
     */
    @Nonnull
    public SettingDef<T> withVisibility(final BiPredicate<RecipeContext, Map<String, Object>> condition) {
        return toBuilder().visibility(condition)
            .build();
    }
}
