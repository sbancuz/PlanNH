package com.sbancuz.plannh.data.provider.gregtech;

import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.COIL;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.MODE;
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
 * In shadow mode the log names a machine whose row disagrees with the probe. A machine listed here
 * disagreeing is expected and its reason is printed with it. A machine <em>not</em> listed here
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
                .knobs(COIL));

        // Its setupProcessingLogic dereferences the circuit imprint, which only exists once a player
        // has imprinted a placed machine.
        put(
            "bartworks.common.tileentities.multis.MTECircuitAssemblyLine",
            "reaches for world state while setting up, so a probe clone cannot be asked",
            GTMachinePreset.builder()
                .perfectOC()
                .knobs(MODE));

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
                .knobs(COIL));
    }

    @Nullable
    static GTMachinePreset lookup(final String className) {
        final Override found = BY_CLASS.get(className);
        return found == null ? null : found.preset();
    }

    /**
     * Why this machine is not read from GregTech, or null when it is. Walks superclasses for the same
     * reason {@link GTMachinePresets#lookup} does: a subclass inherits its parent's row, so it
     * inherits the parent's reason for disagreeing with the probe rather than reading as news.
     */
    @Nullable
    public static String reason(@Nonnull final Class<?> mteClass) {
        for (Class<?> c = mteClass; c != null; c = c.getSuperclass()) {
            final Override found = BY_CLASS.get(c.getName());
            if (found != null) return found.reason();
        }
        return null;
    }

    @Nonnull
    static Iterable<String> keys() {
        return BY_CLASS.keySet();
    }
}
