package com.sbancuz.plannh.data.provider.enderio;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import crazypants.enderio.power.Capacitors;

/**
 * The capacitor tiers EnderIO ships, read from EnderIO.
 *
 * <p>
 * A capacitor is EnderIO's coil: the player picks it, and it sets the speed of every powered machine.
 * {@code AbstractPoweredMachineEntity.getPowerUsePerTick} returns the capacitor's
 * {@code getMaxEnergyExtracted}, and a task advances by that much per tick, so a recipe's duration is its
 * energy divided by this number. The range is 20 to 1900 RF/t, a 95x spread in recipe duration.
 *
 * <p>
 * Read from EnderIO, not hard-coded: EnderIO ships ten capacitor items for seven tiers (Silver, Endergetic
 * and Endergised duplicate Basic, Advanced and Ender), so a hand-written list would have to pick which
 * duplicates to drop and would break when EnderIO adds one.
 */
public final class EnderIOCapacitors {

    private EnderIOCapacitors() {}

    /** One tier: its name and the RF/t it supplies a machine. */
    public record Tier(String label, int rfPerTick) {}

    private static final List<Tier> TIERS = read();

    @Nonnull
    private static List<Tier> read() {
        final List<Tier> tiers = new ArrayList<>();
        int highestSeen = 0;
        for (final Capacitors capacitor : Capacitors.VALUES) {
            // declaration order is ascending tier, duplicates last, so a tier not above highestSeen is one
            if (capacitor.capacitor.getTier() <= highestSeen) continue;
            highestSeen = capacitor.capacitor.getTier();
            tiers.add(new Tier(capacitor.oreDict, capacitor.capacitor.getMaxEnergyExtracted()));
        }
        return List.copyOf(tiers);
    }

    /** The strongest capacitor this pack ships, the tier an untouched row is planned with. */
    public static int highestTier() {
        return TIERS.size() - 1;
    }

    /** Clamped, so a chart saved against a pack with more capacitors still resolves to a real one. */
    @Nonnull
    private static Tier at(final int tier) {
        return TIERS.get(Math.max(0, Math.min(TIERS.size() - 1, tier)));
    }

    /** The energy this tier supplies to a recipe each tick, which sets the recipe's duration. */
    public static int rfPerTick(final int tier) {
        return at(tier).rfPerTick();
    }

    /** EnderIO's name for the capacitor, so the row prints the item a player crafts. */
    @Nonnull
    public static String label(final int tier) {
        return at(tier).label();
    }
}
