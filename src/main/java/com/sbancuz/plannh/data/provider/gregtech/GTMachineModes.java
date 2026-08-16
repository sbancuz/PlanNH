package com.sbancuz.plannh.data.provider.gregtech;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.PlanNH;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;

/**
 * How many modes a machine has, and which recipe implies which.
 *
 * <p>
 * Several multiblocks are two machines behind one controller, and the mode selects which - the
 * Advanced Distillation Tower is a distillery or a tower, the Ore Washing Plant an ore washer or a
 * simple washer. GregTech expresses that as {@code getRecipeMap()} returning a different map per
 * mode, so for those the mode is a function of the recipe. A node already knows which recipemap its
 * recipe came from, and asking the user on top of that offers one right answer and several wrong
 * ones - picking tower mode for a distillery recipe models a machine that cannot run the recipe at
 * all, and nothing says so. So the mapping is inverted here and the mode is looked up.
 *
 * <p>
 * The count is separate from that, and is needed even when the mapping is ambiguous: it is what
 * bounds the row a machine still has to ask for, and what bounds the sweep the sensitivity scan
 * makes. GregTech stores it nowhere - {@code nextMachineMode} is the authority, and its own javadoc
 * tells a machine author to override it "if you have more than 2 modes" - so it is the length of the
 * cycle that method describes.
 *
 * <p>
 * This reads only public GregTech API, so it works whether or not the machine probe is switched on.
 */
public final class GTMachineModes {

    private GTMachineModes() {}

    /** A mode cycle that does not come back around is broken, not interesting. */
    private static final int MAX_MODES = 16;

    private static final Modes SINGLE = new Modes(1, null);

    /**
     * @param count       how many modes the machine cycles through, at least one.
     * @param byRecipeMap recipemap name to the mode that selects it, or null when two modes share a
     *                    recipemap and the recipe therefore cannot say which is meant.
     */
    public record Modes(int count, @Nullable Map<String, Integer> byRecipeMap) {

        /** The mode this recipe implies, or -1 when the user still has to say. */
        public int modeFor(@Nullable final RecipeMap<?> recipeMap) {
            if (byRecipeMap == null || recipeMap == null) return -1;
            return byRecipeMap.getOrDefault(recipeMap.unlocalizedName, -1);
        }
    }

    /** Walks a prototype's mode cycle. Clones first, because machineMode feeds tooltips and NEI. */
    @Nonnull
    public static Modes of(@Nonnull final IMetaTileEntity prototype) {
        if (!(prototype instanceof MTEMultiBlockBase)) return SINGLE;
        try {
            if (!(prototype.newMetaEntity(null) instanceof final MTEMultiBlockBase machine)) return SINGLE;
            return of(machine);
        } catch (final RuntimeException | LinkageError e) {
            PlanNH.LOG.debug("PlanNH: {} would not report its modes", prototype.getClass(), e);
            return SINGLE;
        }
    }

    /** As above, for a caller that already holds a clone it may write to. */
    @Nonnull
    public static Modes of(@Nonnull final MTEMultiBlockBase machine) {
        if (!machine.supportsMachineModeSwitch()) return SINGLE;
        try {
            final Map<String, Integer> byRecipeMap = new HashMap<>();
            boolean distinct = true;
            int count = 0;
            int mode = 0;
            while (count < MAX_MODES) {
                machine.machineMode = mode;
                count++;

                final RecipeMap<?> map = machine.getRecipeMap();
                // Two modes on one recipemap: the recipe no longer says which, so ask after all. The
                // count still stands, because the machine still cycles through them.
                if (map == null || byRecipeMap.putIfAbsent(map.unlocalizedName, mode) != null) distinct = false;

                final int next = machine.nextMachineMode();
                if (next == 0) break;
                mode = next;
            }
            if (count < 2) return SINGLE;
            return new Modes(count, distinct ? Map.copyOf(byRecipeMap) : null);
        } catch (final RuntimeException | LinkageError e) {
            PlanNH.LOG.debug("PlanNH: {} would not report its modes", machine.getClass(), e);
            return SINGLE;
        }
    }
}
