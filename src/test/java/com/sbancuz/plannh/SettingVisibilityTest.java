package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.properties.RecipeProperty;

/**
 * The machine picker hides every row the selected machine does not use, so a profile may carry far
 * more settings than it renders. These pin the filter itself; what each GT profile chooses to gate
 * is covered by the preset tests.
 */
class SettingVisibilityTest {

    private static final RecipeContext EMPTY = new RecipeContext(new HashMap<RecipeProperty<?>, Object>());

    private static MachineProfile profileOf(final SettingDef<?>... settings) {
        return new MachineProfile(
            "test",
            "Test",
            List.of(settings),
            (s, ctx) -> new EffectResult(ctx.getOrDefault(RecipePropertyAPI.DURATION_TICKS, 0), 0, 1));
    }

    @Test
    void settingsAreVisibleUnlessGated() {
        final SettingDef<Integer> plain = SettingDef.intDef("plain", 1, 0, 10);

        assertTrue(plain.isVisible(EMPTY, Map.of()));
        assertEquals(
            1,
            profileOf(plain).visibleSettings(EMPTY, Map.of())
                .size());
    }

    @Test
    void aGatedSettingDropsOutOfTheRenderedRows() {
        final SettingDef<Integer> shown = SettingDef.intDef("shown", 1, 0, 10);
        final SettingDef<Integer> hidden = SettingDef.intDef("hidden", 1, 0, 10)
            .withVisibility((ctx, s) -> false);

        final List<SettingDef<?>> visible = profileOf(shown, hidden).visibleSettings(EMPTY, Map.of());

        assertEquals(1, visible.size());
        assertEquals("shown", visible.get(0).key);
    }

    @Test
    void visibilityReadsTheLiveSettingsMap() {
        final SettingDef<Integer> gated = SettingDef.intDef("gated", 1, 0, 10)
            .withVisibility((ctx, s) -> MachineProfile.getBool(s, "advanced", false));
        final MachineProfile profile = profileOf(gated);

        assertTrue(
            profile.visibleSettings(EMPTY, Map.of())
                .isEmpty());
        assertEquals(
            1,
            profile.visibleSettings(EMPTY, Map.of("advanced", true))
                .size());
    }

    /**
     * Settings.X.def() hands out one shared instance per enum constant, so a profile conditioning a
     * setting must not condition it for every other profile too.
     */
    @Test
    void withVisibilityCopiesRatherThanMutatingTheSharedDef() {
        final SettingDef<Integer> shared = SettingDef.intDef("shared", 7, 0, 10);
        final SettingDef<Integer> gated = shared.withVisibility((ctx, s) -> false);

        assertNotSame(shared, gated);
        assertTrue(shared.isVisible(EMPTY, Map.of()));
        assertFalse(gated.isVisible(EMPTY, Map.of()));
        assertEquals(shared.key, gated.key);
        assertEquals(shared.defaultValue, gated.defaultValue);
        assertEquals(shared.minInt, gated.minInt);
        assertEquals(shared.maxInt, gated.maxInt);
    }
}
