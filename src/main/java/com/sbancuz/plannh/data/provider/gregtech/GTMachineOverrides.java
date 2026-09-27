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
 * The only numbers PlanNH asserts about a machine against what the machine reports about itself, for
 * the handful the probe cannot read or reads wrongly. Each row states which, and being named here is
 * what takes the machine off the probe - {@code GTMachineIndex.sourceOf} reads the row instead.
 *
 * <p>
 * Read by hand from GT5-Unofficial at tag <b>5.09.52.613</b>, so nothing here notices a GregTech
 * change; {@code GTImplementationDigestTest} fails when one of these classes moves. If a chart looks
 * wrong for one of these machines, suspect this file first.
 */
public final class GTMachineOverrides {

    private GTMachineOverrides() {}

    /** A row and why it is here. Handed out whole so a caller reads both from the same walk. */
    public record Override(GTMachinePreset preset, String reason) {}

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

    /** A subclass inherits its parent's row, and with it the parent's reason for not being read. */
    @Nullable
    public static Override find(@Nonnull final Class<?> mteClass) {
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
