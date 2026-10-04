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
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.machine.MachineVariant;
import com.sbancuz.plannh.data.machine.MachineVariants;
import com.sbancuz.plannh.data.provider.GTProvider;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IOverclockDescriptionProvider;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.logic.ModifierKind;
import gregtech.api.logic.ModifierRange;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.objects.overclockdescriber.OverclockDescriber;
import gregtech.api.recipe.RecipeMap;

/**
 * GregTech machines that can run each recipemap, read from GT's registry.
 *
 * <p>
 * {@link GregTechAPI#METATILEENTITIES} stores prototype MetaTileEntities with no world and no tile
 * entity. This is the walk GT runs in {@code NEIGTConfig.generateRecipeCatalystIndex}, keyed by
 * recipemap where GT's index is keyed by recipe category. A RecipeMap has no field for whether its
 * recipes run on a singleblock, a multiblock, or both (five machine classes share the macerator map),
 * so the walk reads that off each machine prototype.
 *
 * <p>
 * The cache is static because GregTechAPI's array is fixed once mods have loaded, and the map is
 * immutable after its build. GT's equivalent index is static too.
 */
public final class GTMachineIndex {

    /** Appended to a singleblock's name in the picker. Translated, like the name before it. */
    private static final String SINGLEBLOCK_SUFFIX = "plannh.machine.singleblock_suffix";
    /** Appended to a machine PlanNH has no numbers for, marking why a node using it is not overclocked. */
    private static final String NO_SPEC_SUFFIX = "plannh.machine.no_spec_suffix";

    /** Source of a machine's overclock numbers, set once at indexing. */
    public enum NumberSource {
        /** GregTech's OverclockDescriber, for a machine without a spec. */
        DESCRIBER,
        /** Machine's ProcessingSpec. */
        SPEC,
        /** Neither, so the node uses the recipe's numbers unchanged. */
        NONE
    }

    /**
     * @param id            {@code MetaTileEntity.getLocalNameKey()}, persisted in charts. Not the meta
     *                      id, which GT reassigns between versions, nor the localized name, which is
     *                      locale-dependent. Not getMetaName() either: that is declared on the tile
     *                      entity and cannot be called on the prototypes this walks.
     * @param tieredByBuild true for a multiblock, whose energy hatch the player picks. False for a
     *                      singleblock, whose tier is fixed by the placed block.
     * @param describer     GT's overclock describer for this machine, present on singleblocks and a few
     *                      multis. When set, the overclock comes from it.
     * @param machine       the multiblock's ProcessingSpec, or null without one.
     * @param numberSource  source of this machine's overclock numbers, grouped by MachineTableCommand.
     * @param modes         mode count and each mode's recipemap.
     */
    public record MachineEntry(String id, String displayName, boolean tieredByBuild, int voltageTier, int amperage,
        int catalystPriority, @Nullable OverclockDescriber describer, @Nullable GTMachineSpec machine,
        NumberSource numberSource, GTMachineModes.Modes modes) implements MachineVariant {

        /** Mode that runs this recipemap, or -1 when the user has to pick it. */
        public int modeFor(@Nullable final RecipeMap<?> recipeMap) {
            return modes.modeFor(recipeMap);
        }

        /**
         * A machine with no spec reads no structure, so it has no rows. Guessed rows for a machine
         * PlanNH cannot model would put controls on a node that change nothing.
         */
        @Override
        @Nonnull
        public Set<Settings> settings() {
            return machine == null ? Set.of() : machine.settings();
        }

        /** Values a player builds or inserts for this machine, each with its range. */
        @Nonnull
        public Map<ModifierKind, ModifierRange> structure() {
            return machine == null ? Map.of() : machine.structure();
        }

        /**
         * GT's machine names don't always include the form factor. "Chemical Reactor" against "Large
         * Chemical Reactor" reads as a size, not as singleblock against multiblock, and the two overclock
         * differently. Without the suffix, a player could read the other form's numbers as a bug. Only the
         * label gets it, because displayName is matched against NEI's tab title.
         */
        @Override
        @Nonnull
        public String label() {
            if (!tieredByBuild) return displayName + StatCollector.translateToLocal(SINGLEBLOCK_SUFFIX);
            return isUncovered(this) ? displayName + StatCollector.translateToLocal(NO_SPEC_SUFFIX) : displayName;
        }

        @Override
        @Nullable
        public EffectResult run(final RecipeContext ctx, final Map<String, Object> settings,
            final EffectResult recipe) {
            return GTPresetApplier.run(this, ctx, settings, recipe);
        }
    }

    /**
     * GregTech's source for the shared picker. The index is built on first use, not at registration,
     * because the build reads every machine GT ships, tooltips included.
     */
    public static final MachineVariants.Source SOURCE = new MachineVariants.Source() {

        @Override
        @Nonnull
        public List<? extends MachineVariant> candidates(final RecipeContext ctx) {
            return GTMachineIndex.candidates(ctx);
        }

        @Override
        @Nullable
        public MachineVariant byId(final String id) {
            return GTMachineIndex.byId(id);
        }
    };

    /**
     * Null until first use and replaced whole by {@link #reset()}, since the GregTech registry it
     * mirrors is static: one per client, fixed once mods have loaded.
     */
    @Nullable
    private static Map<String, List<MachineEntry>> byRecipeMap;
    private static Map<String, MachineEntry> byId = Map.of();
    /** Reordered candidate lists, keyed by recipemap and NEI title. */
    private static final Map<String, List<MachineEntry>> byNeiTitle = new HashMap<>();

    /**
     * Last result of {@link #candidates}. One slot is enough: the visibility predicates call it about a
     * dozen times per frame with the same arguments, and the panel draws one node's rows at a time.
     */
    @Nullable
    private static RecipeMap<?> lastMap;
    private static String lastTitle = "";
    @Nullable
    private static List<MachineEntry> lastCandidates;

    private GTMachineIndex() {}

    /** Drops the index and everything derived from it, so a reload rebuilds against the new pack. */
    public static void reset() {
        byRecipeMap = null;
        byId = Map.of();
        byNeiTitle.clear();
        lastMap = null;
        lastTitle = "";
        lastCandidates = null;
    }

    /**
     * Looks a machine up by its persisted id across all recipemaps, so a row can render the name of a
     * machine the current recipe cannot run. Null when the machine is missing from the pack.
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
        final String title = ctx.getOrDefault(GTProvider.NEI_TITLE, "");

        // checked before any build or concatenation, because the miss path allocates a key
        if (recipeMap == lastMap && title.equals(lastTitle) && lastCandidates != null) return lastCandidates;

        final List<MachineEntry> ordered = ensureBuilt().getOrDefault(recipeMap.unlocalizedName, List.of());
        final List<MachineEntry> answer = title.isEmpty() || ordered.size() < 2 ? ordered
            : byNeiTitle
                .computeIfAbsent(recipeMap.unlocalizedName + '\u0000' + title, key -> preferNeiTitle(ordered, title));

        lastMap = recipeMap;
        lastTitle = title;
        lastCandidates = answer;
        return answer;
    }

    /**
     * Moves the candidate named by NEI's tab title to the front, ahead of the simplest-first order,
     * since that title is the machine the recipe list belongs to. Returns the input list when no
     * candidate matches or the match already leads. Memoized per recipemap and title because the
     * visibility predicates call this every frame.
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
     * Machine a node is using, or null when it isn't a GregTech one. Resolution and its per-frame memo
     * are in {@link MachineVariants}, which returns a variant this source supplied. Any other type
     * means the recipe belongs to another mod.
     */
    @Nullable
    public static MachineEntry selected(final RecipeContext ctx, final Map<String, Object> settings) {
        return MachineVariants.selected(ctx, settings) instanceof final MachineEntry entry ? entry : null;
    }

    /**
     * Builds the index ahead of the first frame that would use it. The build reads every GT machine and
     * builds its tooltip, a visible stall inside a draw.
     */
    public static void warmup() {
        ensureBuilt();
    }

    /** Neither GT's describer nor a spec, so its numbers are a generic guess. */
    private static boolean isUncovered(final MachineEntry entry) {
        return entry.machine() == null && entry.describer() == null;
    }

    /**
     * Structure every machine is measured at for picker ranking: an IV hatch and each machine at its best. Only the
     * resulting order is used. No chart reads these numbers.
     */
    private static final StructureState RANKING_REFERENCE = StructureState.of(5, 0);

    /**
     * Max parallels at {@link #RANKING_REFERENCE}, used only to order the picker. The order also sets
     * the default, since a node with no machine set uses the first candidate.
     */
    private static int scale(final MachineEntry entry) {
        if (entry.machine() == null) return 0;
        return entry.machine()
            .spec()
            .getMaxParallel(
                entry.machine()
                    .inputs(RANKING_REFERENCE));
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
                    // a prototype queried outside GT's load order can throw: skip it, keep the picker
                    PlanNH.LOG.warn("PlanNH: skipping GT machine {} while indexing", mte.getClass().getName(), t);
                }
            }
        } catch (final Throwable t) {
            PlanNH.LOG.warn("PlanNH: GT machine index unavailable; falling back to manual settings", t);
            return Map.of();
        }

        // Simplest first, since a player planning a line picks that machine most often and gets it with
        // no clicks. GT's catalyst priority leads where set. After that come singleblocks before
        // multiblocks, then lower tier, then scale, which puts the Large Chemical Reactor ahead of the
        // Mega. An id tiebreak alone would put Mega first.
        final Comparator<MachineEntry> best = Comparator.comparingInt(MachineEntry::catalystPriority)
            .reversed()
            // machine PlanNH has no numbers for is only modelled generically, so it goes last
            .thenComparing(GTMachineIndex::isUncovered)
            .thenComparing(MachineEntry::tieredByBuild)
            .thenComparingInt(MachineEntry::voltageTier)
            .thenComparingInt(GTMachineIndex::scale)
            .thenComparing(MachineEntry::id);
        index.replaceAll((recipeMapId, entries) -> {
            entries.sort(best);
            return Collections.unmodifiableList(entries);
        });

        if (!uncovered.isEmpty()) {
            // without this warning a multiblock with no spec is modelled as a plain 1x node unnoticed
            PlanNH.LOG.warn("PlanNH: {} GT multiblocks declare no processing spec: {}", uncovered.size(), uncovered);
        }
        byId = Map.copyOf(ids);
        return Map.copyOf(index);
    }

    private static void addMachine(final Map<String, List<MachineEntry>> index,
        final Map<String, MachineEntry> ids, final IMetaTileEntity mte, final RecipeMapWorkable workable,
        final Set<String> uncovered) {
        final var recipeMaps = workable.getAvailableRecipeMaps();
        if (recipeMaps.isEmpty()) return;
        // Superseded structures stay registered so existing worlds load, but a recipe converts them
        // away and they cannot be built. They share the display name of the machine that replaced
        // them, so listing both would put the same name in the picker twice. Matching *Legacy class
        // names would skip MTEDroneCentre, MTEFluidShaper and MTELargeBoiler and catch the turbines.
        if (mte instanceof final MTEMultiBlockBase multi && multi.isStructureDeprecated()) return;

        final boolean tieredByBuild = mte instanceof MTEMultiBlockBase;
        final GTMachineModes.Modes modes = GTMachineModes.of(mte);
        final GTMachineSpec machine = GTMachineSpec.read(mte);
        // A spec outranks a describer. GregTech checks at load that each machine runs as its spec
        // computes, and the steam multiblocks share their describer with the steam singleblocks.
        final OverclockDescriber describer = machine == null
            && mte instanceof final IOverclockDescriptionProvider provider ? provider.getOverclockDescriber() : null;
        final NumberSource numberSource = describer != null ? NumberSource.DESCRIBER
            : machine != null ? NumberSource.SPEC : NumberSource.NONE;
        if (machine == null && describer == null && tieredByBuild) {
            uncovered.add(mte.getClass().getName());
        }

        final MachineEntry entry = new MachineEntry(
            mte.getLocalNameKey(),
            mte.getLocalName(),
            tieredByBuild,
            mte instanceof final MTETieredMachineBlock tiered ? tiered.mTier : 0,
            mte instanceof final MTEBasicMachine basic ? basic.mAmperage : 1,
            workable.getRecipeCatalystPriority(),
            describer,
            machine,
            numberSource,
            modes);

        // two machines sharing an id would render as each other in the picker
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
