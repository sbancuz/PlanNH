package com.sbancuz.plannh.data.machine;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.EffectResult;

/**
 * One machine a recipe could be run in.
 *
 * <p>
 * An interface rather than a record because the provider that owns a machine already has an object
 * describing it, holding far more than a picker needs - GregTech's carries an overclock describer, a
 * parameter preset and a mode table. Copying those into a shared record would make the copy a second
 * authority on what the machine is, and would cost a lookup back to the original every time anything
 * wanted the parts the copy dropped.
 */
public interface MachineVariant {

    /**
     * What a chart persists. Must be stable across game versions and independent of locale, because a
     * chart saved today has to resolve to the same machine on a pack updated tomorrow.
     */
    @Nonnull
    String id();

    /** The machine's own name, as the recipe list titles it. */
    @Nonnull
    String displayName();

    /** The settings this machine reads, which are exactly the rows a node offers for it. */
    @Nonnull
    Set<Settings> settings();

    /**
     * What the picker shows. Defaults to the machine's name; a provider overrides it where the name
     * alone is ambiguous, and the two are kept apart because the name is also matched against the
     * recipe list's tab title.
     */
    @Nonnull
    default String label() {
        return displayName();
    }

    /**
     * Whether the machine's throughput comes from what a player builds around it - an energy hatch, a
     * capacitor - rather than from the block itself. A machine that fixes its own has nothing to ask,
     * so the rows that would let a player choose are hidden and the machine's own number is used.
     *
     * <p>
     * True by default, which is also what an unrecognised machine reads as: the rows this gates are
     * the ones a built machine has, and offering them on a machine that turns out to fix its own is
     * recoverable where withholding them is not.
     */
    default boolean tieredByBuild() {
        return true;
    }

    /**
     * What the machine makes of the recipe: how long it takes, what it draws, and how many copies run
     * at once. {@code recipe} is the recipe as the list states it, before any machine touched it.
     *
     * <p>
     * This is the whole of "the machine supplies the numbers", so a provider gets that behaviour by
     * implementing this rather than by writing an effect step of its own. Null means the machine
     * cannot answer - it has no parameters for this recipe, or the recipe carries something no
     * machine can report - and leaves the node on its own settings rows.
     */
    @Nullable
    default EffectResult run(final RecipeContext ctx, final Map<String, Object> settings, final EffectResult recipe) {
        return null;
    }
}
