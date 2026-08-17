package com.sbancuz.plannh.data.machine;

import java.util.Set;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.data.Settings;

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
    Set<Settings> knobs();

    /**
     * What the picker shows. Defaults to the machine's name; a provider overrides it where the name
     * alone is ambiguous, and the two are kept apart because the name is also matched against the
     * recipe list's tab title.
     */
    @Nonnull
    default String label() {
        return displayName();
    }
}
