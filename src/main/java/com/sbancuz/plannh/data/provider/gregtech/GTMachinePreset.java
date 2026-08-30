package com.sbancuz.plannh.data.provider.gregtech;

import java.util.EnumSet;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.Settings;

/**
 * What one GregTech machine does to a recipe, in the terms
 * {@link gregtech.api.util.OverclockCalculator} takes. This mirrors GT's own
 * {@code ProcessingLogic} setters one-to-one - {@code setMaxParallelSupplier} /
 * {@code setSpeedBonusSupplier} / {@code setEuModifierSupplier} - so a row can be diffed against the
 * MetaTileEntity it came from, which is the only way a table this size stays honest across GT
 * updates.
 *
 * <p>
 * Everything is a function of {@link StructureState} because the interesting machines derive their
 * numbers from blocks the player placed. Constants go through
 * {@link Builder#speed(double)}-style shorthands.
 */
public record GTMachinePreset(ToDoubleFunction<StructureState> durationModifier,
    ToDoubleFunction<StructureState> euModifier, ToIntFunction<StructureState> maxParallel,
    ToDoubleFunction<StructureState> eutIncreasePerOC, ToDoubleFunction<StructureState> durationDecreasePerOC,
    ToIntFunction<StructureState> machineHeat, boolean heatOC, boolean heatDiscount, int recipeHeatOverride,
    int maxTierSkips, boolean unlimitedTierSkips, @Nullable RecipeOverride recipeOverride, EnumSet<Settings> settings) {

    /** A machine that ignores the recipe's own cost, like the Multi Smelter's fixed 4 EU/t over 128t. */
    public record RecipeOverride(int eut, int duration) {}

    /** {@link #maxTierSkips()} sentinel: leave OverclockCalculator's own default of 1. */
    public static final int TIER_SKIPS_UNSET = -1;
    /** {@link #recipeHeatOverride()} sentinel: use the recipe's own required heat. */
    public static final int RECIPE_HEAT_FROM_RECIPE = -1;

    public boolean usesHeat() {
        return heatOC || heatDiscount;
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private ToDoubleFunction<StructureState> durationModifier = s -> 1.0;
        private ToDoubleFunction<StructureState> euModifier = s -> 1.0;
        private ToIntFunction<StructureState> maxParallel = s -> 1;
        private ToDoubleFunction<StructureState> eutIncreasePerOC = s -> 4.0;
        private ToDoubleFunction<StructureState> durationDecreasePerOC = s -> 2.0;
        private ToIntFunction<StructureState> machineHeat = s -> 0;
        private boolean heatOC;
        private boolean heatDiscount;
        private int recipeHeatOverride = RECIPE_HEAT_FROM_RECIPE;
        private int maxTierSkips = TIER_SKIPS_UNSET;
        private boolean unlimitedTierSkips;
        @Nullable
        private RecipeOverride recipeOverride;
        private final EnumSet<Settings> settings = EnumSet.noneOf(Settings.class);

        private Builder() {}

        /**
         * The duration multiplier, i.e. what GT passes to setSpeedBonus. A machine documented as
         * "2.25x faster" takes {@code speed(1 / 2.25)}.
         */
        public Builder speed(final double durationMultiplier) {
            return speed(s -> durationMultiplier);
        }

        public Builder speed(final ToDoubleFunction<StructureState> fn) {
            this.durationModifier = fn;
            return this;
        }

        public Builder eu(final double modifier) {
            return eu(s -> modifier);
        }

        public Builder eu(final ToDoubleFunction<StructureState> fn) {
            this.euModifier = fn;
            return this;
        }

        public Builder parallel(final int fixed) {
            this.maxParallel = s -> fixed;
            return this;
        }

        public Builder parallelPerVoltageTier(final int perTier) {
            this.maxParallel = s -> perTier * s.voltageTier();
            return this;
        }

        public Builder parallel(final ToIntFunction<StructureState> fn) {
            this.maxParallel = fn;
            return this;
        }

        /** GT's enablePerfectOverclock(): 4x EU for 4x speed instead of 4x for 2x. */
        public Builder perfectOC() {
            return overclock(4.0, 4.0);
        }

        /** GT's setOverclock(timeReduction, powerIncrease). */
        public Builder overclock(final double durationDecrease, final double eutIncrease) {
            this.durationDecreasePerOC = s -> durationDecrease;
            this.eutIncreasePerOC = s -> eutIncrease;
            return this;
        }

        public Builder overclock(final ToDoubleFunction<StructureState> durationDecrease,
            final ToDoubleFunction<StructureState> eutIncrease) {
            this.durationDecreasePerOC = durationDecrease;
            this.eutIncreasePerOC = eutIncrease;
            return this;
        }

        /** How hot the machine runs. Only read when it also overclocks or discounts on heat. */
        public Builder machineHeat(final ToIntFunction<StructureState> machineHeatFn) {
            this.machineHeat = machineHeatFn;
            return this;
        }

        /** Heat-driven perfect overclocks, one per 1800K of headroom over the recipe. */
        public Builder heatOC(final ToIntFunction<StructureState> machineHeatFn) {
            this.heatOC = true;
            return machineHeat(machineHeatFn);
        }

        /** The EBF's 5% EU/t discount per 900K of headroom. Nearly always paired with heatOC. */
        public Builder heatDiscount() {
            this.heatDiscount = true;
            return this;
        }

        /** For machines that overclock against a fixed heat floor rather than the recipe's. */
        public Builder recipeHeat(final int heat) {
            this.recipeHeatOverride = heat;
            return this;
        }

        public Builder maxTierSkips(final int skips) {
            this.maxTierSkips = skips;
            return this;
        }

        public Builder unlimitedTierSkips() {
            this.unlimitedTierSkips = true;
            return this;
        }

        public Builder recipeOverride(final int eut, final int duration) {
            this.recipeOverride = new RecipeOverride(eut, duration);
            return this;
        }

        public Builder settings(final Settings... used) {
            for (final Settings setting : used) {
                settings.add(setting);
            }
            return this;
        }

        @Nonnull
        public GTMachinePreset build() {
            return new GTMachinePreset(
                durationModifier,
                euModifier,
                maxParallel,
                eutIncreasePerOC,
                durationDecreasePerOC,
                machineHeat,
                heatOC,
                heatDiscount,
                recipeHeatOverride,
                maxTierSkips,
                unlimitedTierSkips,
                recipeOverride,
                EnumSet.copyOf(settings));
        }
    }
}
