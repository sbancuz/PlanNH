package com.sbancuz.plannh.data.provider.gregtech;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.data.Settings;

/**
 * The structure a player built around a machine, as far as the overclock math cares. A prototype
 * MetaTileEntity cannot report any of this - it only exists once blocks are placed - so it is user
 * input, and each machine preset declares which fields it actually reads via
 * {@link GTMachinePreset#knobs()}.
 *
 * <p>
 * Tier numbering follows GregTech's own, not the block list: {@code coilTier} is
 * {@link gregtech.api.enums.HeatingCoilLevel#getTier()}, i.e. {@code ordinal - 2}, so 0 is
 * Cupronickel. How far each field goes is {@link GTStructureTiers}.
 */
public record StructureState(int voltageTier, int coilTier, int solenoidTier, int itemPipeTier, int pipeCasingTier,
    int sawbladeTier, int electrodeTier, int structureTier, int width, int mode) {

    /**
     * The settings a GregTech machine reads as structure, in the order this record stores them. One
     * authority: the probe's sensitivity scan sweeps these, {@link #slotOf} maps them to components,
     * and a machine table lists them. {@link Settings} holds many more settings than these, so the
     * switch below can no longer be exhaustive by construction - {@code StructureStateWithTest} is
     * what now catches a knob added here without a slot.
     */
    public static final Set<Settings> KNOBS = Collections.unmodifiableSet(
        EnumSet.of(
            Settings.GT_COIL,
            Settings.GT_SOLENOID,
            Settings.GT_ITEM_PIPE,
            Settings.GT_PIPE_CASING,
            Settings.GT_SAWBLADE,
            Settings.GT_ELECTRODE,
            Settings.GT_STRUCTURE_TIER,
            Settings.GT_WIDTH,
            Settings.GT_MODE));

    /**
     * The same structure with one knob moved, which is how the probe finds out whether a knob matters.
     *
     * <p>
     * The array literal is in record-component order, and {@link #slotOf} says which slot each knob
     * writes. Both are stated rather than derived from {@code Settings.ordinal()}: an enum reordered
     * for display would otherwise silently move every knob onto its neighbour's field.
     */
    @Nonnull
    public StructureState with(@Nonnull final Settings knob, final int tier) {
        final int[] tiers = { voltageTier, coilTier, solenoidTier, itemPipeTier, pipeCasingTier, sawbladeTier,
            electrodeTier, structureTier, width, mode };
        tiers[slotOf(knob)] = tier;
        return new StructureState(
            tiers[0],
            tiers[1],
            tiers[2],
            tiers[3],
            tiers[4],
            tiers[5],
            tiers[6],
            tiers[7],
            tiers[8],
            tiers[9]);
    }

    /** Which component a knob writes. Voltage is slot 0 and is not a knob, so no case yields it. */
    private static int slotOf(@Nonnull final Settings knob) {
        return switch (knob) {
            case GT_COIL -> 1;
            case GT_SOLENOID -> 2;
            case GT_ITEM_PIPE -> 3;
            case GT_PIPE_CASING -> 4;
            case GT_SAWBLADE -> 5;
            case GT_ELECTRODE -> 6;
            case GT_STRUCTURE_TIER -> 7;
            case GT_WIDTH -> 8;
            case GT_MODE -> 9;
            default -> throw new IllegalArgumentException(knob + " is not a structure knob");
        };
    }
}
