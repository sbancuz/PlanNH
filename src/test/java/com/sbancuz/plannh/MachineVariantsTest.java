package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.machine.MachineVariant;
import com.sbancuz.plannh.data.machine.MachineVariants;
import com.sbancuz.plannh.data.properties.RecipeProperty;

/**
 * The machine picker without GregTech. Everything here is a made-up mod, which is the point: the
 * layer earns its place only if a provider can offer machines, get the picker row and get per-machine
 * settings rows without PlanNH knowing anything about it.
 */
class MachineVariantsTest {

    private static final RecipeProperty<String> KIND = RecipeProperty.<String>builder("test_kind", "")
        .build();

    private record Fake(String id, String displayName, Set<Settings> settings, String label)
        implements MachineVariant {}

    private static final Fake SIMPLE = new Fake("mod:simple", "Simple Grinder", Set.of(), "Simple Grinder");
    private static final Fake FANCY = new Fake(
        "mod:fancy",
        "Fancy Grinder",
        Set.of(Settings.GT_COIL),
        "Fancy Grinder (big)");

    /** Answers for one recipe kind and nothing else, the way a real provider recognises its own. */
    private record FakeSource(String kind, List<Fake> offered) implements MachineVariants.Source {

        @Override
        public List<? extends MachineVariant> candidates(final RecipeContext ctx) {
            return kind.equals(ctx.getOrDefault(KIND, "")) ? offered : List.of();
        }

        @Override
        @Nullable
        public MachineVariant byId(final String id) {
            for (final Fake fake : offered) {
                if (fake.id()
                    .equals(id)) return fake;
            }
            return null;
        }
    }

    private static RecipeContext recipeOf(final String kind) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>();
        props.put(KIND, kind);
        return new RecipeContext(props);
    }

    private static Map<String, Object> chose(final String id) {
        final Map<String, Object> settings = new HashMap<>();
        settings.put(Settings.MACHINE.key(), id);
        return settings;
    }

    @BeforeEach
    @AfterEach
    void isolate() {
        MachineVariants.reset();
    }

    @Test
    void aProviderThatDoesNotRecogniseTheRecipeOffersNothing() {
        MachineVariants.register(new FakeSource("grinding", List.of(SIMPLE, FANCY)));

        assertTrue(
            MachineVariants.candidates(recipeOf("smelting"))
                .isEmpty());
        assertNull(MachineVariants.selected(recipeOf("smelting"), Map.of()));
    }

    /** Nothing stored is the normal state, so the first candidate has to be a usable answer on its own. */
    @Test
    void anUnsetNodeUsesTheBestCandidateAndStoresNothing() {
        MachineVariants.register(new FakeSource("grinding", List.of(SIMPLE, FANCY)));
        final Map<String, Object> settings = new HashMap<>();

        assertSame(SIMPLE, MachineVariants.selected(recipeOf("grinding"), settings));
        assertTrue(settings.isEmpty(), "resolving a machine must not write one");
    }

    @Test
    void aStoredMachineIsHonoured() {
        MachineVariants.register(new FakeSource("grinding", List.of(SIMPLE, FANCY)));

        assertSame(FANCY, MachineVariants.selected(recipeOf("grinding"), chose("mod:fancy")));
    }

    /** A pack that dropped the mod must not silently become a different machine with different numbers. */
    @Test
    void aMachineThePackNoLongerHasResolvesToNothing() {
        MachineVariants.register(new FakeSource("grinding", List.of(SIMPLE, FANCY)));

        assertNull(MachineVariants.selected(recipeOf("grinding"), chose("mod:removed")));
    }

    /** The rows a node offers are the selected machine's, which is the whole point of registering settings. */
    @Test
    void settingRowsFollowTheSelectedMachine() {
        MachineVariants.register(new FakeSource("grinding", List.of(SIMPLE, FANCY)));
        final RecipeContext ctx = recipeOf("grinding");

        assertFalse(
            MachineVariants.usesSetting(Settings.GT_COIL)
                .test(ctx, chose("mod:simple")));
        assertTrue(
            MachineVariants.usesSetting(Settings.GT_COIL)
                .test(ctx, chose("mod:fancy")));
    }

    /** The row stores an id and shows a label, so a save stays stable while the row reads as a name. */
    @Test
    void thePickerOffersIdsAndShowsLabels() {
        MachineVariants.register(new FakeSource("grinding", List.of(SIMPLE, FANCY)));

        assertEquals(
            List.of("mod:simple", "mod:fancy"),
            MachineVariants.pickerDef()
                .options(recipeOf("grinding")));
        assertEquals(
            "Fancy Grinder (big)",
            MachineVariants.pickerDef()
                .display("mod:fancy"));
    }

    /**
     * A node's recipe came from one mod's recipe list, so the first source that recognises it is the
     * answer. Merging would mean offering machines that cannot run the recipe.
     */
    @Test
    void onlyTheProviderThatRecognisesTheRecipeAnswers() {
        MachineVariants.register(new FakeSource("smelting", List.of(SIMPLE)));
        MachineVariants.register(new FakeSource("grinding", List.of(FANCY)));

        assertEquals(List.of(FANCY), MachineVariants.candidates(recipeOf("grinding")));
    }

    /** Registration is per-pass; a stale source would offer machines from a mod that just went away. */
    @Test
    void resetDropsEverySource() {
        MachineVariants.register(new FakeSource("grinding", List.of(SIMPLE)));
        MachineVariants.reset();

        assertTrue(
            MachineVariants.candidates(recipeOf("grinding"))
                .isEmpty());
    }
}
