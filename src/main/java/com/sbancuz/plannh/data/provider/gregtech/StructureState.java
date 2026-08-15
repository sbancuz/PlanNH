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
 * Cupronickel. How far each field goes is {@link GTStructureTiers}.
 */
public record StructureState(int voltageTier, int coilTier, int solenoidTier, int itemPipeTier, int pipeCasingTier,
    int sawbladeTier, int electrodeTier, int structureTier, int width, int mode) {}
