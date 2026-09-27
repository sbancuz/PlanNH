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
    /** What an unset enum row resolves to; the first option when the setting does not say. */
    @Nullable
    private final Function<RecipeContext, String> defaultFn;
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
    /**
     * The value that means the machine did nothing here, in this row's own units. Declared rather than
     * inferred from {@link #defaultValue}, which for an auto row is the unset marker, or from the
     * {@code Settings} constant a provider borrowed the key from, whose scale can differ.
     */
    @Nullable
    private final Integer neutral;

    @Builder(toBuilder = true, access = AccessLevel.PRIVATE)
    private SettingDef(final String key, final Class<T> type, final T defaultValue, final int minInt, final int maxInt,
        @Nullable final Function<RecipeContext, List<String>> optionsFn,
        @Nullable final Function<RecipeContext, String> defaultFn, @Nullable final UnaryOperator<String> displayFn,
        @Nullable final BiFunction<T, MachineConfig, String> badgeFn,
        @Nullable final BiPredicate<RecipeContext, Map<String, Object>> visibility,
        @Nullable final ToIntBiFunction<RecipeContext, Map<String, Object>> autoValueFn,
        @Nullable final ToIntBiFunction<RecipeContext, Map<String, Object>> maxFn, @Nullable final Integer neutral) {
        this.neutral = neutral;
        this.key = key;
        this.label = StatCollector.translateToLocal("plannh.settings." + key);
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
        this.maxFn = maxFn;
    }

    /** Whether this row is reporting that the machine did nothing, in its own units. */
    public boolean isNeutral(final int value) {
        return neutral != null && neutral == value;
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
     * A fixed list of choices. An empty list is a setting with nothing to offer, and stays without an
     * options function at all, so {@link #hasOptions} can answer without a recipe to evaluate against.
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
     * A setting whose choices belong to the mod that owns the machine. PlanNH names the key; the
     * provider attaches the real def when it builds its profile, the way {@code GTProvider} does for
     * the voltage and coil rows.
     *
     * <p>
     * Nothing is declared about the domain, deliberately. A placeholder range or option list would be
     * a second authority on what the mod allows, and it would be wrong the day the mod added a tier -
     * which is the whole failure this indirection exists to prevent. Until a provider supplies a def,
     * {@link #hasOptions} answers false and the row offers nothing.
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
     * A setting whose useful value comes from the machine rather than from a fixed default - the
     * parallel count, the overclock factors, the coil heat. Nothing stored means "whatever the
     * machine does", so the row shows a real number and the node never freezes a value that goes
     * stale when its machine or structure changes.
     */
    @Nonnull
    public static SettingDef<Integer> autoIntDef(final String key, final int min, final int max,
        final ToIntBiFunction<RecipeContext, Map<String, Object>> autoValueFn,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn) {
        return autoIntDef(key, min, max, null, autoValueFn, badgeFn);
    }

    /** As above, declaring the value that means this machine did nothing. */
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
     * An auto row the machine also caps, for the one case where what it does and the most it can do
     * are the same number. Everywhere else the automatic value is a starting point and capping the row
     * at it would leave the row unable to move up from what it already shows.
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
     * The boolean form. Shares {@code autoValueFn} rather than adding a parallel field: a flag is
     * just an int the caller reads as zero or not.
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
     * What the row shows and the maths uses: the stored override, or the machine's own value.
     * Presence is the whole test: a stored zero is a deliberate zero.
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
        return optionsFn != null;
    }

    /** The choices valid for this recipe. Empty means the row has nothing to offer and is skipped. */
    @Nonnull
    public List<String> options(final RecipeContext ctx) {
        return optionsFn == null ? List.of() : optionsFn.apply(ctx);
    }

    /**
     * What an unset enum row means. Nothing stored is the normal state - the sparse settings map keeps
     * only what the user chose - so this is the value the row draws and the maths reads, and it must be
     * one answer rather than one per call site. Falls back to the first option, which is why an options
     * list comes back best-choice-first.
     */
    @Nonnull
    public String defaultOption(final RecipeContext ctx) {
        return defaultOption(ctx, options(ctx));
    }

    /** For a caller that already built the list; {@code options} rebuilds it on every call. */
    @Nonnull
    public String defaultOption(final RecipeContext ctx, final List<String> choices) {
        if (defaultFn != null) {
            final String preferred = defaultFn.apply(ctx);
            if (choices.contains(preferred)) return preferred;
        }
        return choices.isEmpty() ? "" : choices.getFirst();
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
     * Renders the row's value as something other than the number it stores - a pipe casing tier as the
     * casing a player places. The stored value stays a plain integer, which is what the tier is to
     * every machine that reads it; only the row reads as the build step.
     */
    @Nonnull
    public SettingDef<T> withDisplay(final UnaryOperator<String> display) {
        return toBuilder().displayFn(display)
            .build();
    }

    /**
     * Chooses what an unset row resolves to, for a setting whose sensible starting value depends on
     * the chart rather than on the setting - the coil a chart plans with. Nothing is stored until the
     * user edits the row, so this moves with the chart instead of freezing into every node.
     */
    @Nonnull
    public SettingDef<T> withDefault(final Function<RecipeContext, String> defaultOption) {
        return toBuilder().defaultFn(defaultOption)
            .build();
    }

    /**
     * Returns a copy, so the shared singleton handed out by {@link Settings#def} stays unconditioned
     * and one profile's gating cannot leak into another's.
     */
    @Nonnull
    public SettingDef<T> withVisibility(final BiPredicate<RecipeContext, Map<String, Object>> condition) {
        return toBuilder().visibility(condition)
            .build();
    }
}
