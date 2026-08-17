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

    /** The picker became everyone's, so a machine chosen under the GregTech-only key has to carry over. */
    @Test
    void aMachineChosenUnderTheOldKeyIsKept() {
        final Map<String, Object> s = loaded("gt_machine", "gt.blockmachines.multimachine.em.blastsmelter");
        GTSettings.migrateLegacyNode(s);

        assertEquals("gt.blockmachines.multimachine.em.blastsmelter", s.get(Settings.MACHINE.key()));
        assertFalse(s.containsKey("gt_machine"), "the old key would be a second answer to the same question");
    }

    /**
     * Picking a machine is what a chart does instead of hand-tuning, so a chart that picked one must
     * not be read as predating the picker. It would open in advanced mode and stop following its
     * machine.
     */
    @Test
    void aChartThatPickedAMachineUnderTheOldKeyDoesNotOpenInAdvancedMode() {
        final Map<String, Object> s = loaded("gt_machine", "anything", Settings.SPEED.key(), 250);
        GTSettings.migrateLegacyNode(s);

        assertFalse(GTSettings.isAdvanced(s));
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

    /**
     * The mode comes from the node's recipe now, so a stored one is dropped rather than honoured: a
     * chart saved with tower mode on a distillery recipe describes a machine that cannot run it.
     */
    @Test
    void aStoredModeIsDroppedBecauseTheRecipeSettlesItNow() {
        final Map<String, Object> s = loaded(GTSettings.MODE, 1, GTSettings.COIL, "HV");
        GTSettings.migrateLegacyNode(s);

        assertFalse(s.containsKey(GTSettings.MODE), "a stored mode can contradict the recipe");
        assertEquals("HV", s.get(GTSettings.COIL), "the other structure knobs are untouched");
    }

    /** A mode is a structure knob, so dropping it must not read as a hand-tuned overclock. */
    @Test
    void droppingTheModeDoesNotTriggerTheAdvancedMigration() {
        final Map<String, Object> s = loaded(GTSettings.MODE, 1);
        GTSettings.migrateLegacyNode(s);

        assertFalse(GTSettings.isAdvanced(s));
        assertTrue(s.isEmpty(), "nothing else was stored, so nothing else may appear");
    }
}
