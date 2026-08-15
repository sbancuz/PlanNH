package com.sbancuz.plannh.data.provider.gregtech.probe;

/**
 * Everything one probe of a machine observed: the parallel count its processing logic settled on,
 * and every overclock field of the {@code OverclockCalculator} GregTech built for the probe recipe.
 *
 * <p>
 * Record equality is the whole comparison the knob sensitivity scan needs - two readings differ if
 * and only if changing a structure knob changed something a chart would show.
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
}
