package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;

import gregtech.api.enums.GTValues;

/**
 * A machine below the recipe's own EU/t cannot run it, so those tiers are not offered and a stored
 * one is floored rather than honoured. There is also no "off": a GT node always draws power, and an
 * unset tier means the recipe's minimum, not nothing.
 */
class GTVoltageFloorTest {

    @Test
    void theMinimumTierIsTheOneThatCoversTheRecipe() {
        // 480 EU/t needs HV (512), not MV (128).
        assertEquals(3, GTSettings.minimumVoltageTier(480L));
        assertEquals(1, GTSettings.minimumVoltageTier(GTValues.V[1]));
        assertEquals(5, GTSettings.minimumVoltageTier(GTValues.V[5]));
    }

    @Test
    void theOfferedTiersStartAtTheMinimumAndOffIsNotOneOfThem() {
        final List<String> options = GTSettings.voltageOptions(480L);

        assertEquals(GTValues.VN[3], options.getFirst(), "the list starts at the recipe's own tier");
        assertFalse(options.contains("OFF"), "a GT node always draws power");
        assertFalse(options.contains(GTValues.VN[2]), "a tier that cannot run the recipe is not a choice");
        assertTrue(options.contains(GTValues.VN[GTValues.VN.length - 2]), "MAX is still reachable");
    }

    @Test
    void anUnsetTierMeansTheRecipeMinimum() {
        assertEquals(3, GTSettings.voltageTier(480L, Map.of()));
    }

    /** Old charts stored "OFF", and charts can be hand-edited; neither may drop below the floor. */
    @Test
    void aStoredTierBelowTheMinimumIsFloored() {
        assertEquals(3, GTSettings.voltageTier(480L, Map.of(Settings.VOLTAGE.key(), GTValues.VN[1])));
        assertEquals(3, GTSettings.voltageTier(480L, Map.of(Settings.VOLTAGE.key(), "OFF")));
    }

    @Test
    void aStoredTierAboveTheMinimumIsHonoured() {
        assertEquals(7, GTSettings.voltageTier(480L, Map.of(Settings.VOLTAGE.key(), GTValues.VN[7])));
    }

    /** A recipe with no EU cost at all still needs a usable tier rather than an empty list. */
    @Test
    void afreeRecipeStillOffersEveryTier() {
        assertEquals(0, GTSettings.minimumVoltageTier(0L));
        assertEquals(
            GTValues.VN[0],
            GTSettings.voltageOptions(0L)
                .getFirst());
    }
}
