package com.sbancuz.plannh.data.provider.gregtech;

import javax.annotation.Nonnull;

/**
 * The structure a player built around a machine, as far as the overclock math cares. A prototype
 * MetaTileEntity cannot report any of this - it only exists once blocks are placed - so it is user
 * input, and each machine preset declares which fields it actually reads via
 * {@link GTMachinePreset.Knob}.
 *
 * <p>
 * Tier numbering follows GregTech's own, not the block list: {@code coilTier} is
 * {@link gregtech.api.enums.HeatingCoilLevel#getTier()}, i.e. {@code ordinal - 2}, so 0 is
 * Cupronickel. How far each field goes is {@link GTStructureTiers}.
 */
public record StructureState(int voltageTier, int coilTier, int solenoidTier, int itemPipeTier, int pipeCasingTier,
    int sawbladeTier, int electrodeTier, int structureTier, int width, int mode) {

    /**
     * The same structure with one knob moved, which is how the probe finds out whether a knob matters.
     *
     * <p>
     * The array literal is in record-component order, and {@link #slotOf} says which slot each knob
     * writes. Both are stated rather than derived from {@code Knob.ordinal()}: an enum reordered for
     * display would otherwise silently move every knob onto its neighbour's field.
     */
    @Nonnull
    public StructureState with(@Nonnull final GTMachinePreset.Knob knob, final int tier) {
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
    private static int slotOf(@Nonnull final GTMachinePreset.Knob knob) {
        return switch (knob) {
            case COIL -> 1;
            case SOLENOID -> 2;
            case ITEM_PIPE -> 3;
            case PIPE_CASING -> 4;
            case SAWBLADE -> 5;
            case ELECTRODE -> 6;
            case STRUCTURE_TIER -> 7;
            case WIDTH -> 8;
            case MODE -> 9;
        };
    }
}
