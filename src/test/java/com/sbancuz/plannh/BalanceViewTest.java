package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceView;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceView.Boundary;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceView.Kind;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;
import com.sbancuz.plannh.data.flowchart.balancer.Note;
import com.sbancuz.plannh.data.flowchart.balancer.SolverMessage;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Alternative;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Alternatives;
import com.sbancuz.plannh.data.serialization.Serializer;
import com.sbancuz.plannh.harness.GtnhFlowLoader;

/**
 * The choices workflow driven end to end without a screen. Everything the canvas and the summary
 * panel show about the chart's boundary is decided in {@link BalanceView}, so this exercises the
 * same rows a player reads - what is voided, what the alternatives are, what picking one does -
 * rather than asserting against solver internals nobody sees.
 */
class BalanceViewTest {

    private static Graph chart(final String name) {
        return GtnhFlowLoader.load(name)
            .graph();
    }

    private static Alternatives alternatives(final Graph graph) {
        return Balancer.alternatives(BalanceMode.AUTO, graph);
    }

    private static List<Summary.Line.Choice> choicesOf(final List<Summary.Line<?>> rows) {
        return rows.stream()
            .filter(Summary.Line.Choice.class::isInstance)
            .map(Summary.Line.Choice.class::cast)
            .toList();
    }

    /** Choice rows split into their decisions; a heading starts a new one. */
    private static List<List<Summary.Line.Choice>> decisions(final List<Summary.Line<?>> rows) {
        final List<List<Summary.Line.Choice>> blocks = new ArrayList<>();
        for (final Summary.Line<?> line : rows) {
            if (line instanceof Summary.Line.Heading || blocks.isEmpty()) blocks.add(new ArrayList<>());
            if (line instanceof Summary.Line.Choice choice) {
                blocks.getLast()
                    .add(choice);
            }
        }
        return blocks;
    }

    /** The one option matching {@code role}, failing rather than picking when there is not exactly one. */
    private static Alternative onlyOption(final List<Alternative> options, final Predicate<Alternative> role) {
        final List<Alternative> matching = options.stream()
            .filter(role)
            .toList();
        assertEquals(1, matching.size(), () -> "expected exactly one such option, got " + matching);
        return matching.get(0);
    }

    private static List<Boundary> of(final List<Boundary> flows, final Kind kind) {
        return flows.stream()
            .filter(b -> b.kind() == kind)
            .toList();
    }

    @Test
    void surplusIsNamedAtItsPortAndReadsAsSomethingToCollect() {
        // The reported bug in one assertion: the surplus is pinned to the port it leaves by, and
        // named as a surplus rather than as destruction - a player empties that bus like any other.
        final List<Boundary> flows = chart("excess_choice").boundary();

        final List<Boundary> voided = of(flows, Kind.EXCESS);
        assertEquals(1, voided.size());
        assertEquals(
            "nitrogen",
            voided.get(0)
                .ingredient());
        assertEquals(
            530.0,
            voided.get(0)
                .ratePerSecond(),
            1e-4);
        assertEquals(
            SolverMessage.BOUNDARY_EXCESS,
            voided.get(0)
                .label()
                .message(),
            () -> "reads as surplus, not as waste: " + voided.get(0)
                .label());

        assertTrue(
            of(flows, Kind.PRODUCT).stream()
                .noneMatch(
                    b -> b.ingredient()
                        .equals("nitrogen")),
            "and is not double-counted as a separate product line");
        assertTrue(
            of(flows, Kind.PRODUCT).stream()
                .anyMatch(
                    b -> b.ingredient()
                        .equals("coal tar")),
            "while the thing the chart is for still does");
        assertTrue(
            of(flows, Kind.SUPPLY).stream()
                .anyMatch(
                    b -> b.ingredient()
                        .equals("oak wood")));
    }

    @Test
    void aBalancedChartHasNothingToChooseAndNothingToVoid() {
        final Graph graph = chart("light_fuel");
        assertTrue(
            alternatives(graph).options()
                .isEmpty(),
            "no gates, so no question to ask");
        assertTrue(of(graph.boundary(), Kind.EXCESS).isEmpty(), "and no surplus anywhere");
    }

    @Test
    void everyAnswerIsLabelledAndExactlyOneIsMarkedCurrent() {
        for (final String name : List.of("symmetric_choice", "excess_choice", "mk1", "loopGraph")) {
            final Alternatives a = alternatives(chart(name));
            assertFalse(
                a.options()
                    .size() < 2,
                () -> name + " should offer a choice");
            for (final List<Alternative> decision : a.options()
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(Alternative::replaces))
                .values()) {
                assertEquals(
                    1,
                    decision.stream()
                        .filter(Alternative::isCurrent)
                        .count(),
                    () -> name + " must mark one option per decision as the one on screen");
            }
            for (final Summary.Line.Choice row : choicesOf(BalanceView.toLineChoices(chart(name), a))) {
                assertNotNull(row.label(), () -> name + " has an unlabelled row");
                if (!row.active()) {
                    assertNotNull(row.reason(), () -> name + " listed an alternative with no reason given");
                }
            }
        }
    }

    @Test
    void theHeuristicThatPickedTheAnswerIsStatedInWords() {
        // mk1's default beats its alternative on a constant somebody chose, not on a measurement.
        // If that sentence ever stops reaching the panel, the tilt is invisible again.
        final Graph graph = chart("mk1");
        final Alternatives a = alternatives(graph);
        final Alternative alternative = onlyOption(a.options(), o -> !o.isCurrent());
        assertEquals(
            SolverMessage.REASON_IMPORTS_INSTEAD,
            alternative.toNote()
                .message());

        final List<Summary.Line.Choice> rows = choicesOf(BalanceView.toLineChoices(graph, a));
        final Summary.Line.Choice current = onlyRow(rows, Summary.Line.Choice::active);
        assertEquals(
            SolverMessage.BOUNDARY_EXCESS,
            current.label()
                .message(),
            () -> "default leaves a surplus: " + current.label());
        assertEquals(
            SolverMessage.BOUNDARY_ADD,
            onlyRow(rows, r -> !r.active()).label()
                .message(),
            () -> "alternative imports: " + onlyRow(rows, r -> !r.active()).label());
    }

    @Test
    void pickingAnAnswerChangesTheChartAndSticksAcrossASave() {
        final Graph graph = chart("symmetric_choice");
        final Alternatives before = alternatives(graph);
        final Alternative alternative = before.options()
            .stream()
            .filter(o -> !o.isCurrent())
            .findFirst()
            .orElseThrow();
        final Alternative current = onlyOption(before.options(), Alternative::isCurrent);
        assertFalse(
            BalanceView.toLineChoices(graph, before).stream()
                .filter(Summary.Line.Choice.class::isInstance)
                .map(Summary.Line.Choice.class::cast)
                .filter(r -> r.active())
                .findFirst()
                .orElseThrow()
                .label()
                .equals(alternative.toNote()),
            "the two answers void different things");

        // What the summary panel's click handler does, minus the pixels.
        graph.setExcessChoice(alternative.key());

        final Balancer.Answer picked = Balancer
            .solveWithAlternatives(BalanceMode.AUTO, graph, graph.getExcessChoice(), Map.of());
        assertTrue(
            picked instanceof final Balancer.Answer.Solved solved
                && solved.solution().key.equals(alternative.key()),
            "the picked answer is now the one on screen");
        assertEquals(
            alternative.externals()
                .get(0)
                .port(),
            of(graph.boundary(), Kind.EXCESS).stream()
                .map(Boundary::port)
                .findFirst()
                .orElse(null),
            "and the canvas voids where the user asked");

        assertEquals(
            alternative.key(),
            Serializer.decodeGraph(Serializer.encodeGraph(graph))
                .getExcessChoice(),
            "and the pick survives a save");
    }

    @Test
    void theReasonMatchesWhichWayTheAnswerLeans() {
        // Both lose on the same objective - more external quantity - but one throws away and the
        // other imports, and one sentence cannot honestly describe both.
        assertEquals(
            SolverMessage.REASON_LEAVES_EXCESS,
            onlyOption(alternatives(chart("excess_choice")).options(), o -> !o.isCurrent()).toNote()
                .message());
        assertEquals(
            SolverMessage.REASON_IMPORTS_MORE,
            onlyOption(alternatives(chart("loopGraph")).options(), o -> !o.isCurrent()).toNote()
                .message());
    }

    @Test
    void independentDecisionsAreGroupedRatherThanPooled() {
        // Two branch pairs that never touch, so where each leaves its surplus is a separate
        // question. Flat, this is six answers with no indication that picking one of the first
        // three has nothing to do with the last three.
        final Graph graph = chart("two_decisions");
        final Alternatives a = alternatives(graph);
        final Map<java.util.UUID, List<Alternative>> byDecision = new LinkedHashMap<>();
        for (final Alternative option : a.options()) {
            byDecision.computeIfAbsent(
                option.replaces()
                    .nodeId(),
                k -> new ArrayList<>())
                .add(option);
        }

        assertEquals(2, byDecision.size(), "two questions");
        for (final List<Alternative> decision : byDecision.values()) {
            assertEquals(3, decision.size(), "each with its own three answers");
            assertEquals(
                1,
                decision.stream()
                    .filter(Alternative::isCurrent)
                    .count());
        }
        assertEquals(
            6,
            choicesOf(BalanceView.toLineChoices(graph, a)).stream()
                .map(Summary.Line.Choice::label)
                .distinct()
                .count(),
            "and no answer appears in both");
    }

    @Test
    void aDecisionWithNothingToDecideIsNotShown() {
        // jet_fuel has two open gates but only one of them has any alternative. A heading over a
        // lone row repeating it is an offer that is not being made.
        for (final List<Summary.Line.Choice> block : decisions(
            BalanceView.toLineChoices(chart("jet_fuel"), alternatives(chart("jet_fuel"))))) {
            assertTrue(block.size() > 1, () -> "empty decision shown: " + block);
        }
    }

    @Test
    void aTruncatedListSaysSoRatherThanLookingComplete() {
        // palladium_line has far more candidate swaps than the search will try. Silence here would
        // read as "these are all the answers", which is the one thing it must not claim.
        final Alternatives choices = alternatives(chart("palladium_line"));
        if (!choices.complete()) {
            assertFalse(
                choices.notes()
                    .isEmpty(),
                "an incomplete search has to explain itself");
        }
    }

    @Test
    void alternativesReadAsAListOfAmounts_importsFirstThenAscending() {
        // Under the answer in force, the rows are there to be compared against each other, so they
        // sort like a list of amounts and not like the solver's preference order. Every chart with
        // a choice, because an ordering that only holds on the chart it was written against is not
        // an ordering.
        for (final String name : List.of("symmetric_choice", "excess_choice", "mk1", "loopGraph", "two_decisions")) {
            final Graph graph = chart(name);
            for (final List<Summary.Line.Choice> block : decisions(
                BalanceView.toLineChoices(graph, alternatives(graph)))) {
                boolean seenExcess = false;
                double previous = Double.NEGATIVE_INFINITY;
                for (final Summary.Line.Choice row : block) {
                    if (row.active()) continue; // sorted to the front, not into the amounts
                    final boolean isImport = row.label()
                        .message() == SolverMessage.BOUNDARY_ADD;
                    if (isImport) {
                        assertFalse(seenExcess, () -> name + " puts an import after a surplus: " + block);
                    } else if (!seenExcess) {
                        seenExcess = true;
                        previous = Double.NEGATIVE_INFINITY; // each block ascends on its own
                    }
                    final double rate = rateOf(row.label());
                    assertTrue(
                        rate >= previous,
                        () -> name + " lists " + row.label() + " after a bigger amount: " + block);
                    previous = rate;
                }
            }
        }
    }

    private static Summary.Line.Choice onlyRow(final List<Summary.Line.Choice> rows,
        final Predicate<Summary.Line.Choice> role) {
        final List<Summary.Line.Choice> matching = rows.stream()
            .filter(role)
            .toList();
        assertEquals(1, matching.size(), () -> "expected exactly one such row, got " + matching);
        return matching.get(0);
    }

    /**
     * The number out of the label's rate argument - "33.33/s sulfuric acid" - units and all
     * stripped back off. Reads the label rather than the rate behind it on purpose - the order has
     * to hold for what the panel shows - so a chart whose rows cross a unit boundary (mB against B)
     * does not belong in the list above.
     */
    private static double rateOf(final Note label) {
        final String[] words = ((String) label.args()[0]).split(" ");
        return Double.parseDouble(words[0].replaceAll("[^0-9.].*$", ""));
    }
}
