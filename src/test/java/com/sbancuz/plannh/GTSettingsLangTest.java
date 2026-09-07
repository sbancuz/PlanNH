package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;

/**
 * SettingDef resolves its label eagerly in the constructor, so a setting with no lang entry renders
 * the raw key - "plannh.settings.gt_advanced" - in the node panel. That is invisible headlessly and
 * only shows up in game, which is exactly how it shipped once.
 */
class GTSettingsLangTest {

    private static Set<String> langKeys() throws IOException {
        final Set<String> keys = new HashSet<>();
        try (InputStream in = GTSettingsLangTest.class.getResourceAsStream("/assets/plannh/lang/en_US.lang")) {
            if (in == null) fail("en_US.lang is not on the test classpath");
            for (final String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                final int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) keys.add(
                    line.substring(0, eq)
                        .trim());
            }
        }
        return keys;
    }

    /** Reflected rather than listed, so a setting added later is covered without touching this test. */
    private static List<String> declaredSettingKeys() throws IllegalAccessException {
        final List<String> declared = new ArrayList<>();
        for (final Field field : GTSettings.class.getDeclaredFields()) {
            if (field.getType() == String.class && Modifier.isPublic(field.getModifiers())
                && Modifier.isStatic(field.getModifiers())) {
                declared.add((String) field.get(null));
            }
        }
        return declared;
    }

    @Test
    void everyGtSettingHasALabel() throws Exception {
        final Set<String> lang = langKeys();
        final List<String> missing = new ArrayList<>();

        for (final String key : declaredSettingKeys()) {
            if (!lang.contains("plannh.settings." + key)) missing.add(key);
        }

        assertTrue(missing.isEmpty(), "settings with no lang entry, they will render as raw keys: " + missing);
    }

    /**
     * The same check over the shared vocabulary. GTSettings only names the keys GregTech reaches for
     * directly, so a Settings constant added or restored without a lang line went unnoticed - which is
     * how fuel_efficiency, energy_per_tick and gt_multiblock lost theirs.
     */
    @Test
    void everySharedSettingHasALabel() throws Exception {
        final Set<String> lang = langKeys();
        final List<String> missing = new ArrayList<>();

        for (final Settings setting : Settings.values()) {
            if (!lang.contains("plannh.settings." + setting.key())) missing.add(setting.key());
        }

        assertTrue(missing.isEmpty(), "settings with no lang entry, they will render as raw keys: " + missing);
    }

    @Test
    void theKeysAreActuallyBeingFound() throws Exception {
        assertTrue(declaredSettingKeys().size() >= 10, "reflection found no setting keys, so the test proves nothing");
        assertTrue(langKeys().contains("plannh.settings.machines"), "lang file parsed but looks wrong");
    }
}
