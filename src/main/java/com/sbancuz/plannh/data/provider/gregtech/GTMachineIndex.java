package com.sbancuz.plannh.data.provider.gregtech;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.provider.GTProvider;
import com.sbancuz.plannh.data.provider.gregtech.probe.MachineProbe;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IOverclockDescriptionProvider;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.objects.overclockdescriber.OverclockDescriber;
import gregtech.api.recipe.RecipeMap;

/**
 * Which GregTech machines can run a given recipemap, derived from GT's own registry rather than a
 * table PlanNH would have to maintain.
 *
 * <p>
 * {@link GregTechAPI#METATILEENTITIES} holds prototype MetaTileEntities - no world, no tile entity -
 * so this is the same walk GT does in {@code NEIGTConfig.generateRecipeCatalystIndex}, just keyed by
 * recipemap instead of recipe category. That also settles a question a RecipeMap alone cannot answer:
 * nothing on a RecipeMap says whether its recipes are run by a singleblock, a multiblock, or both
 * (the macerator map is shared by five machine classes), but the machines themselves know.
 *
 * <p>
 * The cache is static because what it mirrors is static: GregTechAPI's array is fixed once mods have
 * loaded, and the map is immutable after the one build. GT keeps its equivalent index the same way.
 */
public final class GTMachineIndex {

    private static final String DEPRECATED_LINE = "GT5U.MBTT.Deprecated.NEI";

    /** How a machine is built, which is what decides whether voltage and parallels are editable. */
    public enum Kind {
        SINGLEBLOCK,
        MULTIBLOCK
    }

    /**
     * @param id        {@code MetaTileEntity.getLocalNameKey()} - the value charts persist. Not the
     *                  meta id, which GT reassigns between versions, nor the localized name, which
     *                  is locale-dependent. Deliberately not getMetaName() either: that is declared
     *                  on the tile entity, not on the prototypes this walks, so it does not identify
     *                  them and several machines collided onto one entry.
     * @param describer GT's own overclock behaviour for this machine, present on singleblocks and
     *                  a handful of multis. When set it is authoritative and no preset is needed.
     * @param preset    structure-derived parameters for multiblocks; null when uncovered.
     */
    public record MachineEntry(String id, String displayName, Kind kind, int voltageTier, int amperage,
        int catalystPriority, @Nullable OverclockDescriber describer, @Nullable GTMachinePreset preset) {}

    @Nullable
    private static Map<String, List<MachineEntry>> byRecipeMap;
    private static Map<String, MachineEntry> byId = Map.of();
    /** Reordered candidate lists, keyed by recipemap and NEI title. Derived, so it is safe to keep. */
    private static final Map<String, List<MachineEntry>> byNeiTitle = new HashMap<>();

    private GTMachineIndex() {}

    /**
     * Looks a machine up by its persisted id regardless of recipemap, so a row can still render the
     * name of a machine the current recipe cannot run. Null once the pack no longer has it.
     */
    @Nullable
    public static MachineEntry byId(final String id) {
        ensureBuilt();
        return byId.get(id);
    }

    /** Machines that can run this node's recipe, best-first. Empty when the recipemap has none. */
    @Nonnull
    public static List<MachineEntry> candidates(final RecipeContext ctx) {
        final RecipeMap<?> recipeMap = ctx.getOrDefault(GTProvider.RECIPE_MAP, null);
        if (recipeMap == null) return List.of();
        final List<MachineEntry> ordered = ensureBuilt().getOrDefault(recipeMap.unlocalizedName, List.of());
        final String title = ctx.getOrDefault(GTProvider.NEI_TITLE, null);
        if (title == null || title.isEmpty() || ordered.size() < 2) return ordered;
        return byNeiTitle
            .computeIfAbsent(recipeMap.unlocalizedName + '\u0000' + title, key -> preferNeiTitle(ordered, title));
    }

    /**
     * NEI's own tab title names the machine the recipe list is for, so when a candidate matches it
     * exactly that is the answer, ahead of any heuristic about which machine is simplest. Returns the
     * cached list untouched unless a match exists and is not already leading. The result is memoized
     * per recipemap and title because this runs every frame from the visibility predicates.
     */
    @Nonnull
    private static List<MachineEntry> preferNeiTitle(final List<MachineEntry> entries, @Nullable final String title) {
        if (title == null || title.isEmpty() || entries.size() < 2) return entries;
        for (int i = 0; i < entries.size(); i++) {
            if (!title.equals(
                entries.get(i)
                    .displayName()))
                continue;
            if (i == 0) return entries;
            final List<MachineEntry> reordered = new ArrayList<>(entries);
            reordered.add(0, reordered.remove(i));
            return Collections.unmodifiableList(reordered);
        }
        return entries;
    }

    /**
     * The machine a node is using. An unset setting resolves to the best candidate, so a node that
     * accepts the obvious answer stores nothing; a stored id the pack no longer has resolves to
     * null rather than quietly becoming a different machine.
     */
    @Nullable
    public static MachineEntry selected(final RecipeContext ctx, final Map<String, Object> settings) {
        final List<MachineEntry> candidates = candidates(ctx);
        if (candidates.isEmpty()) return null;

        final String stored = MachineProfile.getString(settings, GTSettings.MACHINE, "");
        if (stored.isEmpty()) return candidates.getFirst();
        for (final MachineEntry entry : candidates) {
            if (entry.id()
                .equals(stored)) return entry;
        }
        return null;
    }

    /**
     * GT's own deprecation marker, added by MultiblockTooltipBuilder.addStructureDeprecatedLine and
     * readable from the prototype's public description.
     */
    private static boolean isDeprecated(final IMetaTileEntity mte) {
        final String[] description = mte.getDescription();
        if (description == null) return false;
        final String marker = StatCollector.translateToLocal(DEPRECATED_LINE);
        for (final String line : description) {
            if (line != null && line.contains(marker)) return true;
        }
        return false;
    }

    /** Neither GT's own describer nor a preset, so its numbers are a generic guess. */
    private static boolean isUncovered(final MachineEntry entry) {
        return entry.preset() == null && entry.describer() == null;
    }

    /**
     * How far a machine scales, measured at a mid reference structure. Only used to order the
     * picker, never to compute anything, so the reference values need only be consistent.
     */
    private static int scale(final MachineEntry entry) {
        if (entry.preset() == null) return 0;
        return entry.preset()
            .maxParallel()
            .applyAsInt(new StructureState(5, 5, 4, 4, 2, 0, 0, 1, 0, 0));
    }

    @Nonnull
    private static Map<String, List<MachineEntry>> ensureBuilt() {
        Map<String, List<MachineEntry>> index = byRecipeMap;
        if (index != null) return index;

        index = build();
        byRecipeMap = index;
        return index;
    }

    @Nonnull
    private static Map<String, List<MachineEntry>> build() {
        if (!Compat.GREGTECH.isLoaded) return Map.of();

        final Map<String, List<MachineEntry>> index = new HashMap<>();
        final Map<String, MachineEntry> ids = new HashMap<>();
        final Set<String> uncovered = new HashSet<>();
        try {
            for (final IMetaTileEntity mte : GregTechAPI.METATILEENTITIES) {
                if (!(mte instanceof final RecipeMapWorkable workable)) continue;
                try {
                    addMachine(index, ids, mte, workable, uncovered);
                } catch (final Throwable t) {
                    // A prototype reporting its recipemaps is not something GT does outside its own
                    // load order, so one hostile machine must not cost the whole picker.
                    PlanNH.LOG.warn("PlanNH: skipping GT machine {} while indexing", mte.getClass().getName(), t);
                }
            }
        } catch (final Throwable t) {
            PlanNH.LOG.warn("PlanNH: GT machine index unavailable; falling back to manual settings", t);
            return Map.of();
        }

        // Simplest first, because that is the machine someone planning a line reaches for and the one
        // they expect with no clicks. GT's catalyst priority leads where it is set; after that,
        // singleblocks before multiblocks, then by how much the machine scales - which is what puts
        // the Large Chemical Reactor ahead of the Mega, where an id tiebreak had put Mega first.
        final Comparator<MachineEntry> best = Comparator.comparingInt(MachineEntry::catalystPriority)
            .reversed()
            // A machine PlanNH has no numbers for goes last: it can only be modelled generically, so
            // it is never the better default. Without this it sorted first, because scale() has
            // nothing to report for it and zero reads as "simplest".
            .thenComparing(GTMachineIndex::isUncovered)
            .thenComparing(MachineEntry::kind)
            .thenComparingInt(MachineEntry::voltageTier)
            .thenComparingInt(GTMachineIndex::scale)
            .thenComparing(MachineEntry::id);
        index.replaceAll((recipeMapId, entries) -> {
            entries.sort(best);
            return Collections.unmodifiableList(entries);
        });

        if (!uncovered.isEmpty()) {
            // The preset table is pinned to one GT version. This is how a GT update that adds a
            // multiblock shows up, instead of that machine silently modelling as a plain 1x node.
            PlanNH.LOG.warn("PlanNH: {} GT multiblocks have no overclock preset: {}", uncovered.size(), uncovered);
        }
        byId = Map.copyOf(ids);
        return Map.copyOf(index);
    }

    private static void addMachine(final Map<String, List<MachineEntry>> index,
        final Map<String, MachineEntry> ids, final IMetaTileEntity mte, final RecipeMapWorkable workable,
        final Set<String> uncovered) {
        final var recipeMaps = workable.getAvailableRecipeMaps();
        if (recipeMaps.isEmpty()) return;
        // Superseded structures stay registered so existing worlds keep loading, but they are
        // converted away by recipe and cannot be built, and they keep the display name of the
        // machine that replaced them - so offering both just shows the same name twice. GT marks
        // them in the tooltip rather than in the class name: a *Legacy name matches neither way
        // round, missing MTEDroneCentre, MTEFluidShaper and MTELargeBoiler while over-matching the
        // turbines.
        if (isDeprecated(mte)) return;

        final Kind kind = mte instanceof MTEMultiBlockBase ? Kind.MULTIBLOCK : Kind.SINGLEBLOCK;
        final OverclockDescriber describer = mte instanceof final IOverclockDescriptionProvider provider
            ? provider.getOverclockDescriber()
            : null;
        // The table is what a chart reads until the probe has been proven against it machine by
        // machine, so the probe only takes over once it is switched on.
        final GTMachinePreset fromTable = GTMachinePresets.lookup(mte.getClass());
        final GTMachinePreset probed = MachineProbe.probe(mte);
        MachineProbe.reportDisagreement(mte.getClass(), fromTable, probed);
        final GTMachinePreset preset = MachineProbe.drivesNumbers() && probed != null ? probed : fromTable;
        if (preset == null && describer == null && kind == Kind.MULTIBLOCK) {
            uncovered.add(mte.getClass().getName());
        }

        final MachineEntry entry = new MachineEntry(
            mte.getLocalNameKey(),
            mte.getLocalName(),
            kind,
            mte instanceof final MTETieredMachineBlock tiered ? tiered.mTier : 0,
            mte instanceof final MTEBasicMachine basic ? basic.mAmperage : 1,
            workable.getRecipeCatalystPriority(),
            describer,
            preset);

        // Two machines sharing an id would silently render as one another in the picker, which is
        // exactly what getMetaName() did here.
        final MachineEntry clash = ids.putIfAbsent(entry.id(), entry);
        if (clash != null && clash != entry) {
            PlanNH.LOG.warn("PlanNH: GT machines {} and {} share the id {}", clash.displayName(), entry.displayName(),
                entry.id());
        }
        for (final RecipeMap<?> recipeMap : recipeMaps) {
            index.computeIfAbsent(recipeMap.unlocalizedName, k -> new ArrayList<>())
                .add(entry);
        }
    }
}
