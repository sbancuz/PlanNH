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

    /** The same structure with one knob moved, which is how the probe finds out whether a knob matters. */
    @Nonnull
    public StructureState with(@Nonnull final GTMachinePreset.Knob knob, final int tier) {
        return switch (knob) {
            case COIL -> new StructureState(
                voltageTier,
                tier,
                solenoidTier,
                itemPipeTier,
                pipeCasingTier,
                sawbladeTier,
                electrodeTier,
                structureTier,
                width,
                mode);
            case SOLENOID -> new StructureState(
                voltageTier,
                coilTier,
                tier,
                itemPipeTier,
                pipeCasingTier,
                sawbladeTier,
                electrodeTier,
                structureTier,
                width,
                mode);
            case ITEM_PIPE -> new StructureState(
                voltageTier,
                coilTier,
                solenoidTier,
                tier,
                pipeCasingTier,
                sawbladeTier,
                electrodeTier,
                structureTier,
                width,
                mode);
            case PIPE_CASING -> new StructureState(
                voltageTier,
                coilTier,
                solenoidTier,
                itemPipeTier,
                tier,
                sawbladeTier,
                electrodeTier,
                structureTier,
                width,
                mode);
            case SAWBLADE -> new StructureState(
                voltageTier,
                coilTier,
                solenoidTier,
                itemPipeTier,
                pipeCasingTier,
                tier,
                electrodeTier,
                structureTier,
                width,
                mode);
            case ELECTRODE -> new StructureState(
                voltageTier,
                coilTier,
                solenoidTier,
                itemPipeTier,
                pipeCasingTier,
                sawbladeTier,
                tier,
                structureTier,
                width,
                mode);
            case STRUCTURE_TIER -> new StructureState(
                voltageTier,
                coilTier,
                solenoidTier,
                itemPipeTier,
                pipeCasingTier,
                sawbladeTier,
                electrodeTier,
                tier,
                width,
                mode);
            case WIDTH -> new StructureState(
                voltageTier,
                coilTier,
                solenoidTier,
                itemPipeTier,
                pipeCasingTier,
                sawbladeTier,
                electrodeTier,
                structureTier,
                tier,
                mode);
            case MODE -> new StructureState(
                voltageTier,
                coilTier,
                solenoidTier,
                itemPipeTier,
                pipeCasingTier,
                sawbladeTier,
                electrodeTier,
                structureTier,
                width,
                tier);
        };
    }
}
