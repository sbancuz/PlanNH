package com.sbancuz.plannh.data.provider.enderio;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import crazypants.enderio.power.Capacitors;

/**
 * The capacitor tiers EnderIO ships, read from EnderIO.
 *
 * <p>
 * A capacitor is EnderIO's coil: it is the block the player chooses, and it sets how fast every
 * powered machine runs. {@code AbstractPoweredMachineEntity.getPowerUsePerTick} returns the
 * capacitor's own {@code getMaxEnergyExtracted}, and a task advances by that much per tick, so a
 * recipe's duration is its energy divided by this number and nothing else. The spread is 20 RF/t to
 * 1900 RF/t, which is a ninety-five-fold difference in how long a recipe takes.
 *
 * <p>
 * Read rather than written down, for the usual reason and one more: EnderIO ships ten capacitor items
 * for seven tiers - the Silver, Endergetic and Endergised capacitors duplicate Basic, Advanced and
 * Ender exactly - so a hand-written list would have to decide which duplicates to drop and would get
 * it wrong the moment EnderIO added one.
 */
public final class EnderIOCapacitors {

    private EnderIOCapacitors() {}

    /** One tier: what it is called and how fast it runs a machine. */
    public record Tier(String label, int rfPerTick) {}

    private static final List<Tier> TIERS = read();

    @Nonnull
    private static List<Tier> read() {
        final List<Tier> tiers = new ArrayList<>();
        int highestSeen = 0;
        for (final Capacitors capacitor : Capacitors.VALUES) {
            // In declaration order, which is ascending tier with the duplicates trailing, so anything
            // not above what we have already taken is one of those duplicates.
            if (capacitor.capacitor.getTier() <= highestSeen) continue;
            highestSeen = capacitor.capacitor.getTier();
            tiers.add(new Tier(capacitor.oreDict, capacitor.capacitor.getMaxEnergyExtracted()));
        }
        return List.copyOf(tiers);
    }

    public static int count() {
        return TIERS.size();
    }

    public static int highestTier() {
        return TIERS.size() - 1;
    }

    /** Clamped, so a chart saved against a pack with more capacitors still resolves to a real one. */
    @Nonnull
    private static Tier at(final int tier) {
        return TIERS.get(Math.max(0, Math.min(TIERS.size() - 1, tier)));
    }

    /** How much energy this tier puts into a recipe each tick, which is what sets its duration. */
    public static int rfPerTick(final int tier) {
        return at(tier).rfPerTick();
    }

    /** EnderIO's own name for the capacitor, so the row reads as the item a player crafts. */
    @Nonnull
    public static String label(final int tier) {
        return at(tier).label();
    }
}
