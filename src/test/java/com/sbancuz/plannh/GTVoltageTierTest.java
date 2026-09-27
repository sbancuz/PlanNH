package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.provider.gregtech.GTOverclockStep;

import gregtech.api.enums.GTValues;

/**
 * The voltage a node overclocks against feeds straight into OverclockCalculator, so a wrong tier
 * value is a wrong EU/t on every GT node at that tier.
 */
class GTVoltageTierTest {

    @Test
    void everyVoltageOptionResolvesToItsGTValue() {
        for (final String option : Settings.VOLTAGE.def()
            .options(new RecipeContext(new HashMap<RecipeProperty<?>, Object>()))) {
            if ("OFF".equals(option)) continue;
            final int tier = indexOf(option);
            assertEquals(GTValues.V[tier], GTOverclockStep.tierNameToVoltage(option), option);
        }
    }

    /** MAX is capped rather than following the tier progression, so it has to come from GTValues. */
    @Test
    void maxTierIsGTsCappedValue() {
        assertEquals(Integer.MAX_VALUE - 7, GTOverclockStep.tierNameToVoltage("MAX"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "OFF", "", "lv", "NOT_A_TIER" })
    void unknownTierNamesDisableOverclocking(final String name) {
        assertEquals(0, GTOverclockStep.tierNameToVoltage(name));
    }

    @Test
    void nullTierNameDisablesOverclocking() {
        assertEquals(0, GTOverclockStep.tierNameToVoltage(null));
    }

    private static int indexOf(final String option) {
        for (int tier = 0; tier < GTValues.VN.length; tier++) {
            if (GTValues.VN[tier].equals(option)) return tier;
        }
        throw new AssertionError("Settings.VOLTAGE offers a tier GTValues.VN does not know: " + option);
    }
}
