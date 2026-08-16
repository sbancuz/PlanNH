package com.sbancuz.plannh.data.provider.gregtech.probe;

import javax.annotation.Nonnull;

/**
 * Everything one probe of a machine observed: the parallel count its processing logic settled on,
 * and every overclock field of the {@code OverclockCalculator} GregTech built for the probe recipe.
 *
 * <p>
 * Record equality is the whole comparison the knob sensitivity scan needs, once {@link #asShown()}
 * has dropped the fields a chart cannot show.
 */
public record ProbeReading(int maxParallel, double durationModifier, double euModifier, double eutIncreasePerOC,
    double durationDecreasePerOC, int maxTierSkip, boolean heatOC, boolean heatDiscount, int machineHeat,
    int recipeHeat, long recipeEUt, int duration, boolean noOverclock, boolean laserOC) {

    /**
     * Whether the numbers describe a machine that could run.
     *
     * <p>
     * A machine is probed with none of its structure around it, and several compute their modifiers
     * from a casing tier that reads as zero there. The Industrial Wire Mill divides by its item pipe
     * tier and comes back with a negative duration; the Thermal Centrifuge comes back drawing no EU
     * at all. Those are not slow machines, they are answers to a question the machine cannot be asked
     * yet, and a chart must not show them.
     */
    public boolean isRunnable() {
        if (durationModifier <= 0 || euModifier <= 0) return false;
        if (eutIncreasePerOC < 1 || durationDecreasePerOC < 1) return false;
        // Overclocking on heat with no heat is the same shape of answer: the coil is not placed yet.
        return !heatOC || machineHeat > 0;
    }

    /** Whether the heat fields mean anything. GT leaves them at zero on a machine that ignores heat. */
    public boolean usesHeat() {
        return heatOC || heatDiscount;
    }

    /**
     * The same reading with everything a chart cannot show removed.
     *
     * <p>
     * GregTech sets a machine heat on machines that never overclock on it - the Plasma Forge spends
     * its heat on deciding which recipes will run, which PlanNH does not model because the user has
     * already chosen the recipe. Comparing raw readings would then let a coil "matter" while moving
     * no number anybody sees, and earn a settings row that does nothing.
     */
    @Nonnull
    public ProbeReading asShown() {
        if (usesHeat()) return this;
        return new ProbeReading(
            maxParallel,
            durationModifier,
            euModifier,
            eutIncreasePerOC,
            durationDecreasePerOC,
            maxTierSkip,
            false,
            false,
            0,
            0,
            recipeEUt,
            duration,
            noOverclock,
            laserOC);
    }
}
