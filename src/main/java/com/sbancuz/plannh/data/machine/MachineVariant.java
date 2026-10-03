package com.sbancuz.plannh.data.machine;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.EffectResult;

/**
 * One machine a recipe can run in.
 *
 * <p>
 * An interface, not a record: the owning provider already has an object per machine with far more than a
 * picker needs (GregTech's has an overclock describer, a parameter preset and a mode table). A shared record
 * would be a second copy of that object, and every dropped field would cost a lookup back to the original.
 */
public interface MachineVariant {

    /**
     * The id stored in the chart. Must be stable across game versions and locales, so a saved chart
     * resolves to the same machine after a pack update.
     */
    @Nonnull
    String id();

    /** The machine's name, as it appears in the recipe list's title. */
    @Nonnull
    String displayName();

    /** The settings this machine reads, which are the rows a node draws for it. */
    @Nonnull
    Set<Settings> settings();

    /**
     * Picker label. Defaults to the machine's name. A provider overrides it where the name alone is
     * ambiguous. The two are separate because the name is also matched against the recipe list's tab
     * title.
     */
    @Nonnull
    default String label() {
        return displayName();
    }

    /**
     * Whether the machine's throughput comes from parts a player builds around it, such as an energy
     * hatch or a capacitor, not from the block. When false, the rows for those parts are hidden and the
     * machine's fixed number is used.
     *
     * <p>
     * True by default, also for an unrecognised machine: these rows belong to a built machine, and
     * showing them on a machine with a fixed number is recoverable where hiding them is not.
     */
    default boolean tieredByBuild() {
        return true;
    }

    /**
     * The machine's numbers for the recipe: duration, draw, and how many copies run at once.
     * {@code recipe} is the recipe as listed, before any machine modified it.
     *
     * <p>
     * A provider makes its machine supply the numbers by implementing this, not by writing an effect
     * step. Null when the machine has no numbers for this recipe, which leaves the node on its settings
     * rows. A machine that won't run the recipe returns {@link EffectResult#rejectedBecause}, and the
     * reason appears on the node.
     */
    @Nullable
    default EffectResult run(final RecipeContext ctx, final Map<String, Object> settings, final EffectResult recipe) {
        return null;
    }
}
