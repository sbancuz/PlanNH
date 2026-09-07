package com.sbancuz.plannh.data.provider.gregtech;

import static com.sbancuz.plannh.data.Settings.GT_COIL;
import static com.sbancuz.plannh.data.Settings.GT_MODE;
import static com.sbancuz.plannh.data.provider.gregtech.GTStructureTiers.clampCoil;
import static com.sbancuz.plannh.data.provider.gregtech.GTStructureTiers.coilHeat;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <h2>Read this file skeptically.</h2>
 *
 * Every row here is a number PlanNH asserts about a machine <b>against</b> what that machine reports
 * about itself. The probe in {@code provider.gregtech.probe} asks each GregTech multiblock to compute
 * its own overclock parameters, and for all but a handful of machines it gets an answer that matches
 * the installed GregTech exactly. These are the handful.
 *
 * <p>
 * A row belongs here only for one of two reasons, and the reason is stated on it:
 *
 * <ol>
 * <li>The probe cannot get an answer at all. The machine has no {@code ProcessingLogic} to read, or it
 * reaches for world state that a probe clone does not have.
 * <li>The probe gets an answer and the answer is wrong. This means the machine derives the value
 * inside {@code checkMachine}, which walks the blocks of a built structure and therefore cannot run.
 * </ol>
 *
 * <p>
 * <b>If a chart looks wrong for one of these machines, suspect this file first.</b> Nothing here is
 * checked against GregTech at runtime. Each row was read by hand from GT5-Unofficial at tag
 * <b>5.09.52.613</b> and will not notice when GregTech changes. That is exactly the failure the probe
 * exists to remove, and these rows are where it could not.
 *
 * <p>
 * Being named here is also what takes a machine off the probe: {@code GTMachineIndex.presetFor} reads
 * the row instead. The probe is still asked, so the disagreement log still covers these machines - one
 * listed here disagreeing is expected and its reason is printed with it, one not listed here
 * disagreeing is news.
 */
public final class GTMachineOverrides {

    private GTMachineOverrides() {}

    /** Why a row is here, printed beside any disagreement the probe reports. */
    private record Override(GTMachinePreset preset, String reason) {}

    private static final Map<String, Override> BY_CLASS = new HashMap<>();

    private static void put(final String className, final String reason, final GTMachinePreset.Builder preset) {
        BY_CLASS.put(className, new Override(preset.build(), reason));
    }

    static {
        // MTEMultiFurnace hand-rolls checkProcessing with a fixed 4 EU/t over 128t and a coil-derived
        // parallel of 4 << (ordinal - 1); getTier() is ordinal - 2, hence the +1.
        put(
            "gregtech.common.tileentities.machines.multi.MTEMultiFurnace",
            "no ProcessingLogic at all - the machine computes its own recipes, so there is nothing to read",
            GTMachinePreset.builder()
                .parallel(s -> 4 << (clampCoil(s.coilTier()) + 1))
                .recipeOverride(4, 128)
                .settings(GT_COIL));

        // Its setupProcessingLogic dereferences the circuit imprint, which only exists once a player
        // has imprinted a placed machine.
        put(
            "bartworks.common.tileentities.multis.MTECircuitAssemblyLine",
            "reaches for world state while setting up, so a probe clone cannot be asked",
            GTMachinePreset.builder()
                .perfectOC()
                .settings(GT_MODE));

        // checkMachine sets mHeatingCapacity to the coil's heat PLUS 100K per voltage tier over MV.
        // The probe writes the coil's heat into that field and cannot add the second term, because the
        // line that adds it only runs while scanning a built structure. So the probe reads 100K per
        // tier low here, and this row is what a chart uses instead.
        put(
            "gregtech.common.tileentities.machines.multi.MTEElectricBlastFurnace",
            "the probe reads 100K per voltage tier low: checkMachine adds a voltage term it cannot run",
            GTMachinePreset.builder()
                .heatOC(s -> coilHeat(s.coilTier()) + 100 * (s.voltageTier() - 2))
                .heatDiscount()
                .settings(GT_COIL));
    }

    /**
     * A subclass inherits its parent's row, and with it the parent's reason for not being read from
     * GregTech - so the walk is here rather than at each caller, and the preset and the reason can
     * never come from different rows.
     */
    @Nullable
    private static Override find(@Nonnull final Class<?> mteClass) {
        for (Class<?> c = mteClass; c != null; c = c.getSuperclass()) {
            final Override found = BY_CLASS.get(c.getName());
            if (found != null) return found;
        }
        return null;
    }

    /** The row that stands in for this machine, or null when GregTech's own answer is used. */
    @Nullable
    public static GTMachinePreset preset(@Nonnull final Class<?> mteClass) {
        final Override found = find(mteClass);
        return found == null ? null : found.preset();
    }

    /** Why this machine is not read from GregTech, or null when it is. */
    @Nullable
    public static String reason(@Nonnull final Class<?> mteClass) {
        final Override found = find(mteClass);
        return found == null ? null : found.reason();
    }

    @Nonnull
    public static Iterable<String> keys() {
        return BY_CLASS.keySet();
    }
}
