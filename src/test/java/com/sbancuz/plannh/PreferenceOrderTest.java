package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Alternative;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Alternatives;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Rank;
import com.sbancuz.plannh.harness.GtnhFlowLoader;

/**
 * The preference list is meant to be the one place the solver's priorities are written down. These
 * are the claims that makes, stated so they fail rather than rot: an order that drifts out of step
 * with the ranking, or a rank the display order has never heard of, is invisible until it changes
 * an answer.
 */
class PreferenceOrderTest {

    @Test
    void theGraphHandsBackNodesAndEdgesInIdOrder() {
        // The model consumes both of these straight rather than sorting them, which is only correct
        // while Graph keeps them sorted - and nothing in the solver would notice a HashMap creeping
        // back in until an answer moved.
        for (final String chart : List.of("mk1", "230_platline", "two_decisions")) {
            final Graph graph = GtnhFlowLoader.load(chart)
                .graph();
            final List<UUID> nodeIds = graph.getNodes()
                .stream()
                .map(n -> n.id)
                .toList();
            final List<UUID> edgeIds = graph.getEdges()
                .stream()
                .map(e -> e.id)
                .toList();
            assertEquals(
                nodeIds.stream()
                    .sorted()
                    .toList(),
                nodeIds,
                chart + " node order");
            assertEquals(
                edgeIds.stream()
                    .sorted()
                    .toList(),
                edgeIds,
                chart + " edge order");
        }
    }

    @Test
    void everyRankTheSolverCanReturnHasAPlaceInTheList() {
        // The old display order was written out by hand beside the preferences. A rank missing from
        // it did not fail anything - List.indexOf returned -1 and sorted it silently to the front,
        // ahead of the answer actually on screen.
        final Set<Rank> reachable = new HashSet<>();
        reachable.add(Rank.DEFAULT);
        reachable.add(Rank.EQUALLY_VALID);
        for (final String chart : GtnhFlowLoader.CORPUS) {
            for (final Alternative option : alternatives(
                GtnhFlowLoader.load(chart)
                    .graph()).options()) {
                reachable.add(option.rank());
            }
        }
        for (final Rank rank : Rank.values()) {
            assertTrue(
                reachable.contains(rank) || unreachableOnThisCorpus(rank),
                () -> rank + " is neither produced by the corpus nor listed as unreachable");
        }
    }

    /**
     * MOVES_LESS needs the tie enumeration to miss a strictly better point, which no corpus chart
     * currently provokes. It stays in the enum because the search is budgeted and can miss one.
     */
    private static boolean unreachableOnThisCorpus(final Rank rank) {
        return rank == Rank.MOVES_LESS;
    }

    @Test
    void theListedAnswersComeBackInOneStableOrder() {
        // Ordering is derived from the preference sequence now, so it cannot disagree with the
        // sequence the solve actually applied. What it must not do is vary between runs.
        for (final String chart : List.of("cetane", "jet_fuel", "symmetric_choice", "mk1")) {
            final List<String> first = ranksOf(chart);
            for (int i = 0; i < 3; i++) {
                assertEquals(first, ranksOf(chart), () -> chart + " listed its answers in a different order");
            }
            assertEquals(
                Rank.DEFAULT.name(),
                first.get(0),
                () -> chart + " must lead with the answer that is actually on screen");
        }
    }

    private static List<String> ranksOf(final String chart) {
        final List<String> ranks = new ArrayList<>();
        for (final Alternative option : alternatives(
            GtnhFlowLoader.load(chart)
                .graph()).options()) {
            ranks.add(
                option.rank()
                    .name());
        }
        return ranks;
    }

    @Test
    void aRejectedAnswerIsExplainedByTheEarliestRuleThatSeparatesIt() {
        // mk1's alternative both imports and moves less material. Reporting the flow would name the
        // milder difference and hide the one that actually decided, which is settled first.
        final List<Alternative> options = alternatives(
            GtnhFlowLoader.load("mk1")
                .graph()).options();

        assertEquals(2, options.size());
        assertEquals(
            Rank.IMPORTS_INSTEAD,
            options.get(1)
                .rank());
        assertNotNull(
            options.get(1)
                .externals()
                .get(0));
        assertTrue(
            options.get(1)
                .externals()
                .get(0)
                .port()
                .input(),
            "and it is genuinely the importing one");
    }

    @Test
    void noAnswerIsListedTwice() {
        for (final String chart : GtnhFlowLoader.CORPUS) {
            final List<Alternative> options = alternatives(
                GtnhFlowLoader.load(chart)
                    .graph()).options();
            final Set<String> seen = new HashSet<>();
            for (final Alternative option : options) {
                assertTrue(
                    seen.add(option.key() + "|" + option.replaces()),
                    () -> chart + " offered the same answer to the same decision twice");
            }
            assertFalse(
                options.stream()
                    .anyMatch(o -> o.rank() == null),
                () -> chart + " produced an option with no rank");
        }
    }

    private static Alternatives alternatives(final Graph graph) {
        return Balancer.alternatives(BalanceMode.AUTO, graph);
    }
}
