package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.provider.gregtech.GTStructureTiers;

import gregtech.api.enums.HeatingCoilLevel;

/**
 * The electrode and sawblade tables are read out of GregTech's own enums by field name, so a rename
 * upstream turns them null and silently drops two machines from the preset table. These tests make
 * that loud, and check the shape of what was read: a field name that still resolves but now means
 * something else would otherwise be indistinguishable from a correct read.
 */
class GTStructureTiersTest {

    @Test
    void gregtechStillExposesTheElectrodeParameters() {
        final GTStructureTiers.Electrodes electrodes = GTStructureTiers.ELECTRODES;
        assertNotNull(electrodes, "ArcFurnaceElectrode no longer reads; check kubatech's field names");

        final int count = electrodes.parallel().length;
        assertTrue(count > 1, "an electrode table of one is not a table");
        assertEquals(count, electrodes.durationModifier().length);
        assertEquals(count, electrodes.durationDecreasePerOC().length);
        assertEquals(count, electrodes.eutIncreasePerOC().length);
        assertEquals(count, electrodes.euModifier().length);
        assertEquals(count - 1, GTStructureTiers.MAX_ELECTRODE_TIER);

        for (int tier = 0; tier < count; tier++) {
            assertTrue(electrodes.parallel()[tier] >= 1, "electrode " + tier + " has no parallel");
            assertTrue(electrodes.durationModifier()[tier] > 0, "electrode " + tier + " has no duration");
            assertTrue(electrodes.euModifier()[tier] > 0, "electrode " + tier + " draws nothing");
            // An overclock that does not at least halve the duration or double the draw is not one.
            assertTrue(electrodes.durationDecreasePerOC()[tier] >= 1, "electrode " + tier + " slows on overclock");
            assertTrue(electrodes.eutIncreasePerOC()[tier] >= 1, "electrode " + tier + " gets cheaper on overclock");
        }
    }

    @Test
    void gregtechStillExposesTheSawbladeParameters() {
        final GTStructureTiers.Sawblades sawblades = GTStructureTiers.SAWBLADES;
        assertNotNull(sawblades, "SawbladeTiers no longer reads; check GT's field names");

        final int count = sawblades.parallelPerVoltageTier().length;
        assertTrue(count > 1, "a sawblade table of one is not a table");
        assertEquals(count, sawblades.durationModifier().length);
        assertEquals(count, sawblades.euModifier().length);
        assertEquals(count - 1, GTStructureTiers.MAX_SAWBLADE_TIER);

        for (int tier = 0; tier < count; tier++) {
            assertTrue(sawblades.parallelPerVoltageTier()[tier] >= 1, "sawblade " + tier + " has no parallel");
            // Every sawblade speeds the machine up, so the duration multiplier is below one. Reading
            // GT's raw speed field instead of the reciprocal it stores would break this.
            assertTrue(sawblades.durationModifier()[tier] < 1, "sawblade " + tier + " slows the machine down");
            assertTrue(sawblades.euModifier()[tier] > 0, "sawblade " + tier + " draws nothing");
        }
    }

    /** The presets clamp coil tiers to this ceiling, so every tier up to it has to be a real coil. */
    @Test
    void everyCoilTierUpToTheCeilingResolves() {
        for (int tier = 0; tier <= GTStructureTiers.MAX_COIL_TIER; tier++) {
            final HeatingCoilLevel level = HeatingCoilLevel.getFromTier((byte) tier);
            assertEquals(tier, level.getTier(), "coil tier " + tier + " does not round-trip");
            assertTrue(level.getHeat() > 0, "coil tier " + tier + " has no heat");
        }
    }
}
