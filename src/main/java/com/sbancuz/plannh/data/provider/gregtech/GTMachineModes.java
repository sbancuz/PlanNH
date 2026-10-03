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
 * Mode count of a machine, and the mode each recipe runs in. Several multiblocks are two machines behind
 * one controller. Where GregTech makes {@code getRecipeMap()} a function of the mode, the mode is derived
 * from the recipe. A user-picked mode could pair tower mode with a distillery recipe, modelling a machine
 * that cannot run it with no error raised.
 *
 * <p>
 * The count bounds the row even where the mapping is ambiguous. Both come from the prototype's
 * {@code getMachineModeCount()} and {@code getRecipeMapForMode(int)}.
 */
public final class GTMachineModes {

    private GTMachineModes() {}

    private static final Modes SINGLE = new Modes(1, null);

    /**
     * @param count       how many modes the machine cycles through, at least one.
     * @param byRecipeMap recipemap name to its mode, or null when two modes share a recipemap, making the
     *                    mode ambiguous.
     */
    public record Modes(int count, @Nullable Map<String, Integer> byRecipeMap) {

        /** Mode that runs this recipemap, or -1 when the user has to pick it. */
        public int modeFor(@Nullable final RecipeMap<?> recipeMap) {
            if (byRecipeMap == null || recipeMap == null) return -1;
            return byRecipeMap.getOrDefault(recipeMap.unlocalizedName, -1);
        }
    }

    /** Reads the modes off the registry prototype, without switching it through them. */
    @Nonnull
    public static Modes of(@Nonnull final IMetaTileEntity prototype) {
        if (!(prototype instanceof final MTEMultiBlockBase machine) || !machine.supportsMachineModeSwitch()) {
            return SINGLE;
        }
        try {
            final int count = machine.getMachineModeCount();
            if (count < 2) return SINGLE;
            final Map<String, Integer> byRecipeMap = new HashMap<>();
            boolean distinct = true;
            for (int mode = 0; mode < count; mode++) {
                final RecipeMap<?> map = machine.getRecipeMapForMode(mode);
                // Two modes on one recipemap: the mode is ambiguous, so the user picks it.
                // The count is kept, since the machine still cycles through every mode.
                if (map == null || byRecipeMap.putIfAbsent(map.unlocalizedName, mode) != null) distinct = false;
            }
            return new Modes(count, distinct ? Map.copyOf(byRecipeMap) : null);
        } catch (final RuntimeException | LinkageError e) {
            PlanNH.LOG.debug("PlanNH: {} would not report its modes", machine.getClass(), e);
            return SINGLE;
        }
    }
}
