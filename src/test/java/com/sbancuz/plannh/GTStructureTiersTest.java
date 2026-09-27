package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.provider.gregtech.GTStructureTiers;

import gregtech.api.enums.HeatingCoilLevel;

class GTStructureTiersTest {

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
