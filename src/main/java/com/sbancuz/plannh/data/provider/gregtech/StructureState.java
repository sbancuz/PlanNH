package com.sbancuz.plannh.data.provider.gregtech;

import java.util.Map;

import javax.annotation.Nonnull;

import gregtech.api.logic.ModifierKind;

/**
 * Structure a player built around a machine, as read by the overclock math: energy hatch tier and amperage, machine
 * mode, and a value for each {@link ModifierKind} the machine's spec reads. Values use GregTech's numbering per kind.
 *
 * <p>
 * A kind missing here reads as the spec's best, so a state stores only values the player set or the chart planned.
 *
 * @param structure Values the player set
 * @param floors    Planned value for each kind the player left unset, raised where the recipe requires more
 */
public record StructureState(int voltageTier, long amperage, int mode, Map<ModifierKind, Long> structure,
    Map<ModifierKind, Long> floors) {

    public StructureState {
        structure = Map.copyOf(structure);
        floors = Map.copyOf(floors);
    }

    public StructureState(final int voltageTier, final long amperage, final int mode,
        final Map<ModifierKind, Long> structure) {
        this(voltageTier, amperage, mode, structure, Map.of());
    }

    @Nonnull
    public static StructureState of(final int voltageTier, final int mode) {
        return new StructureState(voltageTier, 1, mode, Map.of());
    }
}
