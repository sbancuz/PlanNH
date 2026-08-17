package com.sbancuz.plannh.data.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.Settings;

/**
 * Which machines can run a node's recipe, across whichever mods are installed.
 *
 * <p>
 * PlanNH used to answer this twice. A node cycles between the providers that claim its recipe, and
 * GregTech had a picker of its own for the machines inside one provider - two controls, two things to
 * persist, one question. This is the half that generalizes: a mod says which machines it offers for a
 * recipe, and gets the picker row, the per-machine settings rows and the persistence without writing
 * any of them.
 *
 * <p>
 * Static like {@code MachineProfileRegistry} and reset with it from {@code Compat.init}, because
 * providers register into it during the same pass.
 */
public final class MachineVariants {

    private MachineVariants() {}

    /**
     * A provider's answer for its own machines. Deliberately a lookup rather than a list handed over
     * at registration: GregTech derives its 296 entries by cloning and probing every multiblock, which
     * is far too expensive to do during mod init, and it already caches and orders them itself.
     */
    public interface Source {

        /**
         * The machines that can run this recipe, best first - the widget treats the first as what an
         * unset setting means, so the order is the provider's statement about which machine a player
         * planning this line would reach for. Empty when this source does not recognise the recipe.
         */
        @Nonnull
        List<? extends MachineVariant> candidates(RecipeContext ctx);

        /** A machine by its persisted id, whatever recipe it was stored against; null when unknown. */
        @Nullable
        MachineVariant byId(String id);
    }

    private static final List<Source> sources = new ArrayList<>();

    public static void register(@Nonnull final Source source) {
        sources.add(source);
    }

    public static void reset() {
        sources.clear();
        lastCandidates = null;
        lastStored = null;
        lastSelected = null;
    }

    /**
     * The first source that recognises the recipe wins, and nothing is merged. A node's recipe came
     * from one recipe list belonging to one mod, so two sources answering would mean one of them has
     * misidentified it - and concatenating would allocate on a path that runs every frame.
     */
    @Nonnull
    public static List<? extends MachineVariant> candidates(final RecipeContext ctx) {
        for (final Source source : sources) {
            final List<? extends MachineVariant> found = source.candidates(ctx);
            if (!found.isEmpty()) return found;
        }
        return List.of();
    }

    @Nullable
    public static MachineVariant byId(final String id) {
        for (final Source source : sources) {
            final MachineVariant found = source.byId(id);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * The last answer {@link #selected} gave. The settings rows ask which machine a node is using
     * about a dozen times per frame with the same arguments, so the answer is memoized against the
     * candidate list a source handed back - identity, not contents, because a source returns the same
     * cached instance until its answer actually changes.
     */
    @Nullable
    private static List<? extends MachineVariant> lastCandidates;
    @Nullable
    private static String lastStored;
    @Nullable
    private static MachineVariant lastSelected;

    /**
     * The machine a node is using: what it stored, or the best candidate when it stored nothing, so a
     * node that accepts the obvious answer persists nothing at all. A stored id the installed pack no
     * longer has resolves to null rather than quietly becoming a different machine.
     */
    @Nullable
    public static MachineVariant selected(final RecipeContext ctx, final Map<String, Object> settings) {
        final List<? extends MachineVariant> candidates = candidates(ctx);
        if (candidates.isEmpty()) return null;

        final String stored = MachineProfile.getString(settings, Settings.MACHINE.key(), "");
        if (candidates == lastCandidates && stored.equals(lastStored)) return lastSelected;

        lastCandidates = candidates;
        lastStored = stored;
        lastSelected = resolve(candidates, stored);
        return lastSelected;
    }

    @Nullable
    private static MachineVariant resolve(final List<? extends MachineVariant> candidates, final String stored) {
        if (stored.isEmpty()) return candidates.getFirst();
        for (final MachineVariant candidate : candidates) {
            if (candidate.id()
                .equals(stored)) return candidate;
        }
        return null;
    }

    /** Shows a row only when the machine the node selected actually reads that setting. */
    @Nonnull
    public static BiPredicate<RecipeContext, Map<String, Object>> usesKnob(final Settings knob) {
        return (ctx, settings) -> {
            final MachineVariant variant = selected(ctx, settings);
            return variant != null && variant.knobs()
                .contains(knob);
        };
    }

    /**
     * The picker. Options are the machines that can run this node's recipe, best-first, so an unset
     * value renders and behaves as the obvious choice without being serialized. The stored value is
     * the machine's id and the row shows its label, which is what lets a save stay stable while the
     * row reads as a machine name.
     */
    @Nonnull
    public static SettingDef<String> pickerDef() {
        return SettingDef.dynamicEnumDef(Settings.MACHINE.key(), "", ctx -> {
            final List<String> ids = new ArrayList<>();
            for (final MachineVariant variant : candidates(ctx)) {
                ids.add(variant.id());
            }
            return ids;
        }, MachineVariants::labelOf, null);
    }

    @Nonnull
    private static String labelOf(final String id) {
        final MachineVariant variant = byId(id);
        return variant == null ? id : variant.label();
    }
}
