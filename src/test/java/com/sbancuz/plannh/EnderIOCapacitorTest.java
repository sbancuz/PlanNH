package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.effect.steps.CoFHCompat;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.provider.enderio.EnderIOCapacitors;
import com.sbancuz.plannh.data.provider.enderio.EnderIOProfile;

import crazypants.enderio.power.Capacitors;

/**
 * An EnderIO recipe's duration is its energy divided by the capacitor's extract rate.
 */
class EnderIOCapacitorTest {

    /**
     * EnderIO ships ten capacitor items for seven tiers: Silver, Endergetic and Endergised duplicate
     * Basic, Advanced and Ender. Ten steps would mean three of them change nothing.
     */
    @Test
    void theDuplicateCapacitorItemsAreFoldedIntoOneTierEach() {
        assertEquals(10, Capacitors.VALUES.length, "EnderIO changed its capacitor list");
        assertEquals(6, EnderIOCapacitors.highestTier(), "ten items fold to seven tiers");
    }

    /** Read from EnderIO, so a pack that rebalances its capacitors is planned at the pack's numbers. */
    @Test
    void everyTierReportsEnderIOsOwnExtractRate() {
        for (int tier = 0; tier <= EnderIOCapacitors.highestTier(); tier++) {
            assertEquals(
                Capacitors.VALUES[tier].capacitor.getMaxEnergyExtracted(),
                EnderIOCapacitors.rfPerTick(tier),
                "tier " + tier + " does not match the capacitor it stands for");
            assertEquals(Capacitors.VALUES[tier].oreDict, EnderIOCapacitors.label(tier));
        }
    }

    /** Ascending, because the row is stepped and a player expects up to mean faster. */
    @Test
    void theTiersAreOrderedWeakestFirst() {
        for (int tier = 1; tier <= EnderIOCapacitors.highestTier(); tier++) {
            assertTrue(
                EnderIOCapacitors.rfPerTick(tier) > EnderIOCapacitors.rfPerTick(tier - 1),
                "tier " + tier + " is not faster than the one below it");
        }
    }

    /** A tier from a pack with more capacitors than this one still resolves, without throwing. */
    @Test
    void aTierBeyondTheListClampsToIt() {
        assertEquals(
            EnderIOCapacitors.rfPerTick(EnderIOCapacitors.highestTier()),
            EnderIOCapacitors.rfPerTick(EnderIOCapacitors.highestTier() + 5));
        assertEquals(EnderIOCapacitors.rfPerTick(0), EnderIOCapacitors.rfPerTick(-1));
    }

    private static RecipeContext costing(final int rf) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>();
        props.put(CoFHCompat.RF_COST, rf);
        return new RecipeContext(props);
    }

    private static Map<String, Object> atCapacitor(final int tier) {
        final Map<String, Object> settings = new HashMap<>();
        settings.put(Settings.EIO_CAPACITOR.key(), tier);
        return settings;
    }

    /** A better capacitor gives a shorter recipe, in the ratio of the two rates. */
    @Test
    void durationFollowsTheCapacitor() {
        final int top = EnderIOCapacitors.highestTier();

        final int basic = EnderIOProfile.durationTicks(costing(10_000), atCapacitor(0));
        final int totemic = EnderIOProfile.durationTicks(costing(10_000), atCapacitor(top));

        assertEquals(10_000 / EnderIOCapacitors.rfPerTick(0), basic);
        assertEquals(10_000 / EnderIOCapacitors.rfPerTick(top), totemic);
        assertTrue(totemic < basic, "the strongest capacitor must not be the slowest");
    }

    /** A node draws the capacitor's extract rate. */
    @Test
    void thePowerDrawIsTheCapacitorsOwnRate() {
        assertEquals(EnderIOCapacitors.rfPerTick(2), EnderIOProfile.rfPerTick(costing(10_000), atCapacitor(2)));
    }

    /** A node with no capacitor stored is planned at the strongest capacitor, as on a fresh chart. */
    @Test
    void anUnsetNodeUsesTheChartsCapacitor() {
        assertEquals(
            EnderIOCapacitors.rfPerTick(EnderIOCapacitors.highestTier()),
            EnderIOProfile.rfPerTick(costing(10_000), new HashMap<>()));
    }
}
