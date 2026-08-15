package com.sbancuz.plannh.data.provider.gregtech;

/**
 * The structure a player built around a machine, as far as the overclock math cares. A prototype
 * MetaTileEntity cannot report any of this - it only exists once blocks are placed - so it is user
 * input, and each machine preset declares which fields it actually reads via
 * {@link GTMachinePreset.Knob}.
 *
 * <p>
 * Tier numbering follows GregTech's own, not the block list: {@code coilTier} is
 * {@link gregtech.api.enums.HeatingCoilLevel#getTier()}, i.e. {@code ordinal - 2}, so 0 is
 * Cupronickel and 13 is Eternal.
 */
public record StructureState(int voltageTier, int coilTier, int solenoidTier, int itemPipeTier, int pipeCasingTier,
    int sawbladeTier, int electrodeTier, int structureTier, int width, int mode) {

    public static final int MAX_COIL_TIER = 13;
    /** Solenoid tiers are the block meta + 2, so MV is the weakest that exists. */
    public static final int MIN_SOLENOID_TIER = 2;
    public static final int MAX_SOLENOID_TIER = 12;
    public static final int MAX_ITEM_PIPE_TIER = 8;
    public static final int MAX_PIPE_CASING_TIER = 4;
    public static final int MAX_SAWBLADE_TIER = 3;
    public static final int MAX_ELECTRODE_TIER = 13;
    /** Extra Coke Oven slices; also the Dangote tower's height term. */
    public static final int MAX_WIDTH = 15;
}
