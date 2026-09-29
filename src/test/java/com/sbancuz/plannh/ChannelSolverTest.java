package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sbancuz.plannh.data.channels.ChannelProblem;
import com.sbancuz.plannh.data.channels.ChannelProblem.BatchConflict;
import com.sbancuz.plannh.data.channels.ChannelProblem.Feed;
import com.sbancuz.plannh.data.channels.ChannelProblem.PassiveConflict;
import com.sbancuz.plannh.data.channels.ChannelProblem.Recipe;
import com.sbancuz.plannh.data.channels.ChannelSolver;
import com.sbancuz.plannh.data.channels.ChannelSolver.Mode;
import com.sbancuz.plannh.data.channels.ChannelSolver.Solution;
import com.sbancuz.plannh.data.channels.Ingredient;

/**
 * The channel solver on hand-built problems. "#n" is circuit n; other catalysts, items and fluids are
 * named. A batch conflict is "recipe i's batch can be taken by something needing these catalysts"; a
 * passive conflict is "something runs once a passive channel holds these ingredients".
 */
class ChannelSolverTest {

    private static final String CIRCUIT = "circuit";
    private static final Comparator<Ingredient.Item> KEYS = Comparator.<Ingredient.Item>comparingInt(
        i -> CIRCUIT.equals(i.id()) ? i.meta() : Integer.MAX_VALUE)
        .thenComparing(Ingredient.Item::id);
    private static final ChannelSolver.Limits GENEROUS = new ChannelSolver.Limits(60_000, 1_000_000);

    private static Ingredient.Item cat(final String name) {
        return name.startsWith("#") ? new Ingredient.Item(CIRCUIT, Integer.parseInt(name.substring(1)), null)
            : new Ingredient.Item(name, 0, null);
    }

    private static Ingredient.Fluid fluid(final String name) {
        return new Ingredient.Fluid(name);
    }

    private static Ingredient.Item item(final String name) {
        return new Ingredient.Item(name, 0, null);
    }

    private static Set<Ingredient.Item> cats(final String... names) {
        final Set<Ingredient.Item> out = new HashSet<>();
        for (final String n : names) out.add(cat(n));
        return out;
    }

    /** Builder: recipes in order, conflicts by recipe index. */
    private static final class P {

        final Feed feed;
        final List<Recipe> recipes = new ArrayList<>();
        final List<List<BatchConflict>> batchConflicts = new ArrayList<>();
        final List<PassiveConflict> passiveConflicts = new ArrayList<>();

        P(final Feed feed) {
            this.feed = feed;
        }

        /** A recipe with {@code fluids} fluids of its own, an item of its own if {@code bus}. */
        P recipe(final int fluids, final boolean bus, final String... catalysts) {
            final int i = recipes.size();
            final Set<Ingredient> inputs = new HashSet<>();
            for (int f = 0; f < fluids; f++) inputs.add(fluid("r" + i + "f" + f));
            if (bus) inputs.add(item("r" + i + "item"));
            return uses(cats(catalysts), inputs);
        }

        P circuit(final String c) {
            return recipe(1, true, c);
        }

        P uses(final Set<Ingredient.Item> catalysts, final Set<Ingredient> inputs) {
            recipes.add(new Recipe(catalysts, inputs));
            batchConflicts.add(new ArrayList<>());
            return this;
        }

        /** Recipe {@code victim} can be replaced by a non-plan fluid-only recipe needing {@code needs}. */
        P batchConflict(final int victim, final String... needs) {
            return batchConflict(victim, true, -1, needs);
        }

        P batchConflict(final int victim, final boolean fluidsOnly, final int planRecipe, final String... needs) {
            batchConflicts.get(victim)
                .add(new BatchConflict(cats(needs), fluidsOnly, planRecipe));
            return this;
        }

        P passiveConflict(final int owner, final Ingredient... needs) {
            final List<Set<Ingredient>> clauses = new ArrayList<>();
            for (final Ingredient i : needs) clauses.add(Set.of(i));
            passiveConflicts.add(new PassiveConflict(clauses, owner));
            return this;
        }

        ChannelProblem build() {
            return feed == Feed.BATCH ? ChannelProblem.batch(recipes, batchConflicts, KEYS)
                : ChannelProblem.passive(recipes, passiveConflicts, KEYS);
        }

        Map<Mode, Solution> solveAll() {
            final Map<Mode, Solution> out = new EnumMap<>(Mode.class);
            for (final Mode m : Mode.values()) out.put(m, ChannelSolver.solve(build(), m, GENEROUS));
            return out;
        }
    }

    private static P batch() {
        return new P(Feed.BATCH);
    }

    private static P passive() {
        return new P(Feed.PASSIVE);
    }

    private static int[] counts(final Map<Mode, Solution> s) {
        return new int[] { s.get(Mode.NONE)
            .channels()
            .size(),
            s.get(Mode.CIRCUIT)
                .channels()
                .size(),
            s.get(Mode.COLOR)
                .channels()
                .size() };
    }

    private static List<Set<Ingredient.Item>> order(final Solution s) {
        assertEquals(
            1,
            s.channels()
                .size());
        return s.channels()
            .getFirst()
            .checkOrder();
    }

    // region batch feed

    @Test
    void batchConflictsNeedingOnlyTheVictimsOwnCatalystsAreRejected() {
        final P p = batch().circuit("#1")
            .batchConflict(0, "#1");
        assertThrows(IllegalArgumentException.class, p::build);
    }

    @Test
    void recipesWithoutConflictsShareOneChannel() {
        final var s = batch().circuit("#1")
            .circuit("#2")
            .circuit("#3")
            .solveAll();
        assertArrayEquals(new int[] { 1, 1, 1 }, counts(s));
    }

    @Test
    void mutualConflictNeedsTwoChannelsInEveryMode() {
        final var s = batch().circuit("#1")
            .circuit("#2")
            .batchConflict(0, "#2")
            .batchConflict(1, "#1")
            .solveAll();
        assertArrayEquals(new int[] { 2, 2, 2 }, counts(s));
    }

    @Test
    void oneWayConflictIsResolvedByOrder() {
        final var s = batch().circuit("#1")
            .circuit("#2")
            .batchConflict(0, "#2")
            .solveAll();
        assertArrayEquals(new int[] { 2, 1, 1 }, counts(s));
        assertEquals(List.of(cats("#1"), cats("#2")), order(s.get(Mode.CIRCUIT)));
    }

    @Test
    void orderPutsTheVictimFirst() {
        final var s = batch().circuit("#1")
            .circuit("#2")
            .batchConflict(1, "#1")
            .solveAll();
        assertEquals(List.of(cats("#2"), cats("#1")), order(s.get(Mode.COLOR)));
    }

    @Test
    void directedCycleOfThree() {
        final var s = batch().circuit("#1")
            .circuit("#2")
            .circuit("#3")
            .batchConflict(0, "#2")
            .batchConflict(1, "#3")
            .batchConflict(2, "#1")
            .solveAll();
        assertArrayEquals(new int[] { 3, 2, 2 }, counts(s));
    }

    @Test
    void circuitModeCannotOrderBelowAPlanRecipe() {
        // Recipe 1 can take recipe 0's batch and is in the plan: it runs, gets cached, and is retried first
        final var s = batch().circuit("#1")
            .circuit("#2")
            .batchConflict(0, true, 1, "#2")
            .solveAll();
        assertArrayEquals(new int[] { 2, 2, 1 }, counts(s));
    }

    @Test
    void aPlanRecipeInAnotherChannelIsNotCached() {
        // Recipe 2 can take recipe 0's batch, but is kept apart from it by a mutual conflict with recipe 0's catalysts
        final var s = batch().circuit("#1")
            .circuit("#2")
            .circuit("#3")
            .batchConflict(0, true, 2, "#3")
            .batchConflict(2, "#1")
            .solveAll();
        assertEquals(
            2,
            s.get(Mode.CIRCUIT)
                .channels()
                .size());
    }

    @Test
    void aConflictFoundOnlyInItsOwnCheckNeedsThatCheck() {
        // B needs #2 and a lens, which recipes 1 and 2 bring between them; it is found in the check for
        // exactly {#2, lens}, so only recipe 3 gives it a place in the order
        final P p = batch().circuit("#1")
            .circuit("#2")
            .recipe(1, true, "lens")
            .recipe(1, true, "#2", "lens")
            .batchConflict(0, "#2", "lens");
        assertEquals(null, ChannelSolver.checkOrder(p.build(), List.of(0, 1, 2), Mode.CIRCUIT));
        assertNotNull(ChannelSolver.checkOrder(p.build(), List.of(0, 1, 2, 3), Mode.CIRCUIT));
        assertEquals(
            1,
            ChannelSolver.solve(p.build(), Mode.CIRCUIT, GENEROUS)
                .channels()
                .size());
    }

    @Test
    void orderCannotProtectCircuitlessRecipes() {
        final var s = batch().recipe(2, false)
            .circuit("#2")
            .batchConflict(0, "#2")
            .solveAll();
        assertArrayEquals(new int[] { 2, 2, 2 }, counts(s));
    }

    @Test
    void circuitlessIsCheckedLast() {
        final var s = batch().recipe(2, true)
            .circuit("#2")
            .circuit("#1")
            .batchConflict(2, "#2")
            .solveAll();
        assertEquals(List.of(cats("#1"), cats("#2"), Set.of()), order(s.get(Mode.CIRCUIT)));
        assertEquals(List.of(cats("#1"), cats("#2"), Set.of()), order(s.get(Mode.COLOR)));
    }

    @Test
    void colorModeHidesItemsInOtherBuses() {
        // 0's batch is taken through its items (invisible across colors), 1 through fluids
        final var s = batch().circuit("#9")
            .circuit("#1")
            .batchConflict(0, false, -1, "#1")
            .batchConflict(1, "#9")
            .solveAll();
        assertArrayEquals(new int[] { 2, 2, 1 }, counts(s));
        assertEquals(List.of(cats("#1"), cats("#9")), order(s.get(Mode.COLOR)));
    }

    @Test
    void aConflictNeedingTwoBusesCannotRunInColorMode() {
        final var s = batch().circuit("#1")
            .recipe(1, true, "lens")
            .circuit("#2")
            .batchConflict(0, "#2", "lens")
            .solveAll();
        assertArrayEquals(new int[] { 2, 2, 1 }, counts(s));
    }

    // endregion
    // region passive feed

    @Test
    void passiveRecipesSharingInputsShareAChannel() {
        final var s = passive().uses(cats("#1"), Set.of(fluid("water"), item("salt")))
            .uses(cats("#2"), Set.of(fluid("water")))
            .solveAll();
        assertArrayEquals(new int[] { 1, 1, 1 }, counts(s));
    }

    @Test
    void leftoversOfTwoRecipesCanFeedAThird() {
        // Something needs #1 (from recipe 0) and ammonia (from recipe 1): neither alone, both together
        final var s = passive().uses(cats("#1"), Set.of(fluid("water")))
            .uses(cats("#2"), Set.of(fluid("ammonia")))
            .passiveConflict(-1, cat("#1"), fluid("ammonia"))
            .solveAll();
        // Circuit order can't protect a passive line; separate colors can't hide shared fluids
        assertArrayEquals(new int[] { 2, 2, 2 }, counts(s));
    }

    @Test
    void colorsKeepPassiveItemsApart() {
        // Something needs #1 and recipe 1's dust: in color mode they sit in different buses
        final var s = passive().uses(cats("#1"), Set.of(fluid("water")))
            .uses(cats("#2"), Set.of(item("dust")))
            .passiveConflict(-1, cat("#1"), item("dust"))
            .solveAll();
        assertArrayEquals(new int[] { 2, 2, 1 }, counts(s));
    }

    @Test
    void aPlanRecipeRunningElsewhereJoinsThatChannel() {
        // Recipe 1's ingredients sit in any channel holding recipes 0 and 2, so it must be there too
        final P p = passive().uses(cats("#1"), Set.of(fluid("water")))
            .uses(Set.of(), Set.of(fluid("water"), item("dust")))
            .uses(cats("#3"), Set.of(item("dust")))
            .passiveConflict(1, fluid("water"), item("dust"));
        assertEquals(null, ChannelSolver.checkOrder(p.build(), List.of(0, 2), Mode.NONE));
        final Solution s = ChannelSolver.solve(p.build(), Mode.NONE, GENEROUS);
        assertEquals(
            1,
            s.channels()
                .size());
        assertTrue(s.channelsMinimal());
    }

    @Test
    void passiveConflictsOneRecipeFeedsAreRejected() {
        final P p = passive().uses(cats("#1"), Set.of(fluid("water")))
            .passiveConflict(-1, cat("#1"), fluid("water"));
        assertThrows(IllegalArgumentException.class, p::build);
    }

    // endregion
    // region hardware

    @Test
    void colorModeHasSixteenColors() {
        final P p = batch();
        for (int i = 1; i <= 17; i++) p.circuit("#" + i);
        final var s = p.solveAll();
        assertArrayEquals(new int[] { 1, 1, 2 }, counts(s));
        assertEquals(
            2,
            s.get(Mode.COLOR)
                .machines());
    }

    @ParameterizedTest
    @CsvSource({ "0,0,0", "1,0,1", "2,1,0", "3,1,0", "4,1,0", "5,1,1", "6,2,0", "8,2,0", "9,2,1" })
    void fluidHatches(final int fluids, final int quad, final int normal) {
        assertArrayEquals(new int[] { quad, normal }, ChannelSolver.fluidHatches(fluids));
    }

    @Test
    void partsPerMode() {
        // Circuitless fluid-only, #1 with five fluids, #2 with an item
        final var s = batch().recipe(2, false)
            .recipe(5, true, "#1")
            .recipe(1, true, "#2")
            .solveAll();
        assertEquals(
            new ChannelSolver.Parts(1, 1, 1),
            s.get(Mode.NONE)
                .total());
        // The fluid-only circuitless recipe needs no bus of its own
        assertEquals(
            new ChannelSolver.Parts(2, 1, 1),
            s.get(Mode.COLOR)
                .total());
    }

    @Test
    void fluidOnlyCircuitlessChannelNeedsNoBus() {
        final var s = batch().recipe(3, false)
            .solveAll();
        assertEquals(
            new ChannelSolver.Parts(0, 1, 0),
            s.get(Mode.NONE)
                .total());
    }

    @Test
    void tiesAreBrokenByFewestBlocks() {
        // 0 and 2 conflict; 1 fits with either. Pairing the two five-fluid recipes saves a block.
        final var s = batch().recipe(5, true, "#1")
            .recipe(5, true, "#2")
            .recipe(1, true, "#3")
            .batchConflict(0, "#3")
            .solveAll();
        final Solution none = s.get(Mode.NONE);
        assertEquals(
            2,
            none.channels()
                .size());
        assertEquals(
            5,
            none.total()
                .blocks());
        assertEquals(
            List.of(0, 1),
            none.channels()
                .getFirst()
                .members());
    }

    @Test
    void isolatedChannelsShareAMachineAsColors() {
        final var s = batch().circuit("#1")
            .circuit("#2")
            .batchConflict(0, "#2")
            .solveAll();
        assertEquals(
            1,
            s.get(Mode.NONE)
                .machines());
    }

    // endregion
    // region search

    @Test
    void anExpiredClockStillReturnsSoundChannels() {
        for (final Feed feed : Feed.values()) {
            final ChannelProblem problem = random(new Random(1), feed, 24);
            for (final Mode mode : Mode.values()) {
                final Solution s = ChannelSolver.solve(problem, mode, new ChannelSolver.Limits(0, Integer.MAX_VALUE));
                assertFalse(s.channelsMinimal());
                assertFalse(s.blocksMinimal());
                assertSound(problem, s);
            }
        }
    }

    @Test
    void aTinyNodeLimitStillReturnsSoundChannels() {
        for (final Feed feed : Feed.values()) {
            final ChannelProblem problem = random(new Random(2), feed, 24);
            for (final Mode mode : Mode.values()) {
                assertSound(problem, ChannelSolver.solve(problem, mode, new ChannelSolver.Limits(10_000, 1)));
            }
        }
    }

    /** The solver against every partition of small random problems. */
    @Test
    void matchesBruteForce() {
        final Random rng = new Random(42);
        for (int round = 0; round < 60; round++) {
            final Feed feed = round % 2 == 0 ? Feed.BATCH : Feed.PASSIVE;
            final ChannelProblem problem = random(rng, feed, 3 + rng.nextInt(5));
            for (final Mode mode : Mode.values()) {
                final Solution s = ChannelSolver.solve(problem, mode, GENEROUS);
                assertSound(problem, s);
                assertTrue(s.channelsMinimal(), "round " + round + " " + mode);
                assertTrue(s.blocksMinimal(), "round " + round + " " + mode);
                final Best best = bruteForce(problem, mode);
                final String where = "round " + round + " " + feed + " " + mode;
                assertEquals(
                    best.channels(),
                    s.channels()
                        .size(),
                    "channels, " + where);
                assertEquals(
                    best.blocks(),
                    s.total()
                        .blocks(),
                    "blocks, " + where);
                // Among tied splits, the first in recipe order
                assertEquals(best.split(), split(s), "split, " + where);
            }
        }
    }

    @Test
    void repeatedSolvesAgree() {
        // Which tied optimum an ILP returns depends on its internals and on what the JVM has
        // already run; the summary must not flip between them
        final Random rng = new Random(3);
        for (int round = 0; round < 20; round++) {
            final ChannelProblem problem = random(rng, round % 2 == 0 ? Feed.BATCH : Feed.PASSIVE, 12);
            for (final Mode mode : Mode.values()) {
                final Set<Set<Integer>> first = split(ChannelSolver.solve(problem, mode));
                for (int again = 0; again < 5; again++) {
                    assertEquals(first, split(ChannelSolver.solve(problem, mode)), "round " + round + " " + mode);
                }
            }
        }
    }

    @Test
    void realisticChartsSolveWithinTheDefaultLimits() {
        for (final Feed feed : Feed.values()) {
            final ChannelProblem problem = random(new Random(7), feed, 20);
            for (final Mode mode : Mode.values()) {
                // Warm up class loading, which the summary pays once per session
                ChannelSolver.solve(problem, mode);
                final long start = System.nanoTime();
                final Solution s = ChannelSolver.solve(problem, mode);
                final long ms = (System.nanoTime() - start) / 1_000_000;
                assertSound(problem, s);
                assertTrue(s.channelsMinimal() && s.blocksMinimal(), feed + " " + mode + " unproven in " + ms + " ms");
            }
        }
    }

    // endregion
    // region helpers

    /** Every recipe placed exactly once, in channels the mode allows. */
    private static void assertSound(final ChannelProblem problem, final Solution s) {
        final List<Integer> seen = new ArrayList<>();
        for (final ChannelSolver.Channel ch : s.channels()) {
            assertNotNull(ChannelSolver.checkOrder(problem, ch.members(), s.mode()));
            seen.addAll(ch.members());
        }
        seen.sort(Comparator.naturalOrder());
        assertEquals(
            IntStream.range(
                0,
                problem.recipes()
                    .size())
                .boxed()
                .toList(),
            seen);
    }

    private record Best(int channels, int blocks, Set<Set<Integer>> split) {}

    private static Set<Set<Integer>> split(final Solution s) {
        final Set<Set<Integer>> out = new HashSet<>();
        for (final ChannelSolver.Channel c : s.channels()) out.add(Set.copyOf(c.members()));
        return out;
    }

    /** The best valid partition; ties go to the first in enumeration (recipe) order. */
    private static Best bruteForce(final ChannelProblem p, final Mode mode) {
        final int n = p.recipes()
            .size();
        final int[] labels = new int[n];
        final Best[] best = { new Best(Integer.MAX_VALUE, Integer.MAX_VALUE, Set.of()) };
        partitions(labels, 0, 0, () -> {
            final int k = IntStream.of(labels)
                .max()
                .orElse(-1) + 1;
            int blocks = 0;
            final Set<Set<Integer>> split = new HashSet<>();
            for (int c = 0; c < k; c++) {
                final List<Integer> members = new ArrayList<>();
                for (int i = 0; i < n; i++) if (labels[i] == c) members.add(i);
                if (ChannelSolver.checkOrder(p, members, mode) == null) return;
                blocks += ChannelSolver.parts(p, members, mode)
                    .blocks();
                split.add(Set.copyOf(members));
            }
            if (k < best[0].channels() || k == best[0].channels() && blocks < best[0].blocks()) {
                best[0] = new Best(k, blocks, split);
            }
        });
        return best[0];
    }

    /** Restricted growth strings: every set partition exactly once. */
    private static void partitions(final int[] labels, final int i, final int used, final Runnable visit) {
        if (i == labels.length) {
            visit.run();
            return;
        }
        for (int c = 0; c <= used; c++) {
            labels[i] = c;
            partitions(labels, i + 1, Math.max(used, c + 1), visit);
        }
    }

    /**
     * A random problem: a few circuits and a lens, a handful of shared fluids and items, and conflicts
     * of every kind the feed allows (including plan-recipe batch conflicts and owned passive conflicts).
     */
    private static ChannelProblem random(final Random rng, final Feed feed, final int n) {
        final P p = new P(feed);
        final int circuits = Math.max(2, n / 2);
        for (int i = 0; i < n; i++) {
            final Set<Ingredient.Item> catalysts = new HashSet<>();
            if (rng.nextInt(6) > 0) catalysts.add(cat("#" + (1 + rng.nextInt(circuits))));
            if (rng.nextInt(8) == 0) catalysts.add(cat("lens"));
            final Set<Ingredient> inputs = new HashSet<>();
            final int fluids = rng.nextInt(6);
            for (int f = 0; f < fluids; f++) inputs.add(fluid("f" + rng.nextInt(8)));
            if (rng.nextBoolean()) inputs.add(item("i" + rng.nextInt(4)));
            p.uses(catalysts, inputs);
        }
        if (feed == Feed.BATCH) {
            for (int k = 0; k < n + n / 2; k++) {
                final int victim = rng.nextInt(n);
                final Set<Ingredient.Item> needs = new HashSet<>();
                needs.add(cat("#" + (1 + rng.nextInt(circuits))));
                if (rng.nextInt(5) == 0) needs.add(cat("lens"));
                if (p.recipes.get(victim)
                    .catalysts()
                    .containsAll(needs)) continue;
                final int plan = rng.nextInt(4) == 0 ? rng.nextInt(n) : -1;
                p.batchConflicts.get(victim)
                    .add(new BatchConflict(needs, rng.nextBoolean(), plan == victim ? -1 : plan));
            }
        } else {
            final List<Ingredient> pool = new ArrayList<>();
            for (final Recipe r : p.recipes) {
                for (final Ingredient i : r.held()) if (!pool.contains(i)) pool.add(i);
            }
            for (int k = 0; k < n; k++) {
                final List<Set<Ingredient>> needs = new ArrayList<>();
                final int size = 2 + rng.nextInt(2);
                for (int j = 0; j < size; j++) needs.add(Set.of(pool.get(rng.nextInt(pool.size()))));
                final int owner = rng.nextInt(3) == 0 ? rng.nextInt(n) : -1;
                final PassiveConflict c = new PassiveConflict(needs, owner);
                boolean inherent = false;
                for (int i = 0; i < n; i++) inherent |= i != owner && c.metBy(
                    p.recipes.get(i)
                        .held());
                if (!inherent) p.passiveConflicts.add(c);
            }
        }
        return p.build();
    }

    // endregion
}
