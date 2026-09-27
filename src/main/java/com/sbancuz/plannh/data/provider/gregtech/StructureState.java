package com.sbancuz.plannh.data.provider.gregtech;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nonnull;

import gregtech.api.util.tooltip.TooltipTier;

/**
 * The structure a player built around a machine, as far as the overclock math cares: the energy
 * hatch tier, the machine mode, and a value for each structure parameter GregTech declares for the
 * machine through {@code MTEMultiBlockBase.getStructureParametersForInspection}. Values use
 * GregTech's numbering per kind, which {@code gregtech.api.structure.StructureParameter} documents.
 *
 * <p>
 * A kind with no value here reads as the machine's own maximum for it, so a state only has to name
 * what the player chose or the chart decided.
 */
public record StructureState(int voltageTier, int mode, Map<TooltipTier, Integer> structure) {

    public StructureState {
        final EnumMap<TooltipTier, Integer> copy = new EnumMap<>(TooltipTier.class);
        copy.putAll(structure);
        structure = Collections.unmodifiableMap(copy);
    }

    @Nonnull
    public static StructureState of(final int voltageTier, final int mode) {
        return new StructureState(voltageTier, mode, Map.of());
    }

    public int tier(@Nonnull final TooltipTier kind, final int unset) {
        final Integer value = structure.get(kind);
        return value == null ? unset : value;
    }

    /** The same structure with one parameter moved. */
    @Nonnull
    public StructureState with(@Nonnull final TooltipTier kind, final int value) {
        final EnumMap<TooltipTier, Integer> moved = new EnumMap<>(TooltipTier.class);
        moved.putAll(structure);
        moved.put(kind, value);
        return new StructureState(voltageTier, mode, moved);
    }

    @Nonnull
    public StructureState withMode(final int newMode) {
        return new StructureState(voltageTier, newMode, structure);
    }
}
