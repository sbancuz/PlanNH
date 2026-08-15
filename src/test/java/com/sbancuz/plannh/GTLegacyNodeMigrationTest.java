package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;

/**
 * A chart saved before the machine picker existed has its overclock numbers tuned by hand. Honouring
 * a preset instead would silently change what it says, so such a node opens in advanced mode and
 * keeps computing exactly as it did.
 *
 * <p>
 * The settings map is sparse, so what it holds after loading is precisely what the save carried -
 * nothing seeds defaults into it - and the hook can read it directly.
 */
class GTLegacyNodeMigrationTest {

    private static Map<String, Object> loaded(final Object... keyValuePairs) {
        final Map<String, Object> settings = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            settings.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return settings;
    }

    @Test
    void aNodeWithHandTunedSpeedOpensInAdvancedMode() {
        final Map<String, Object> s = loaded(Settings.SPEED.key(), 250);
        GTSettings.migrateLegacyNode(s);

        assertTrue(GTSettings.isAdvanced(s));
    }

    @Test
    void aNodeWithHandTunedHeatOpensInAdvancedMode() {
        final Map<String, Object> s = loaded(Settings.MACHINE_HEAT.key(), 9001);
        GTSettings.migrateLegacyNode(s);

        assertTrue(GTSettings.isAdvanced(s));
    }

    /**
     * Voltage and machine count stay user-owned in both modes, so a chart whose only customisation
     * was "run it at IV, four of them" gets the compact UI rather than being pinned to the old rows.
     */
    @Test
    void voltageAndMachineCountAloneDoNotForceAdvancedMode() {
        final Map<String, Object> s = loaded(Settings.VOLTAGE.key(), "IV", Settings.MACHINES.key(), 4);
        GTSettings.migrateLegacyNode(s);

        assertFalse(GTSettings.isAdvanced(s));
        assertEquals("IV", s.get(Settings.VOLTAGE.key()));
        assertEquals(4, s.get(Settings.MACHINES.key()));
    }

    @Test
    void anUntouchedNodeGetsThePicker() {
        final Map<String, Object> s = loaded();
        GTSettings.migrateLegacyNode(s);

        assertFalse(GTSettings.isAdvanced(s));
        assertTrue(s.isEmpty(), "migration must not write into a node that carried nothing");
    }

    /** Migration is one-way and idempotent: a node that already chose stays as it chose. */
    @Test
    void aNodeThatAlreadyPickedAMachineIsLeftAlone() {
        final Map<String, Object> s = loaded(
            GTSettings.MACHINE,
            "gt.blockmachines.multimachine.blastfurnace.name",
            Settings.SPEED.key(),
            250);
        GTSettings.migrateLegacyNode(s);

        assertFalse(GTSettings.isAdvanced(s), "an explicit machine choice outranks a stale speed value");
    }

    @Test
    void anExplicitAdvancedChoiceIsNotOverwritten() {
        final Map<String, Object> s = loaded(GTSettings.ADVANCED, false, Settings.SPEED.key(), 250);
        GTSettings.migrateLegacyNode(s);

        assertFalse(GTSettings.isAdvanced(s), "the user turning advanced off must stick");
    }
}
