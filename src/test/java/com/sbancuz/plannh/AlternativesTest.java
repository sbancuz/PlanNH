package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer.Answer;
import com.sbancuz.plannh.data.flowchart.balancer.ChoiceKey;
import com.sbancuz.plannh.data.flowchart.balancer.SolverMessage;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Alternative;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Alternatives;
import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Rank;
import com.sbancuz.plannh.data.serialization.Serializer;
import com.sbancuz.plannh.harness.GtnhFlowLoader;
import com.sbancuz.plannh.harness.GtnhFlowLoader.LoadedChart;

class AlternativesTest {

    private static Alternatives alternatives(final String chart) {
        return Balancer.alternatives(
            BalanceMode.AUTO,
            GtnhFlowLoader.load(chart)
                .graph(),
            false);
    }

    @Test
    void symmetricChoice_offersBothMirrorsAsEquallyValid() {
        final Alternatives a = alternatives("symmetric_choice");

        // The two mirrors, plus voiding plate instead - which ties on gates and on excess and only
        // gives up internal flow, so it is a real answer somebody might want (both furnaces full).
        assertEquals(
            3,
            a.options()
                .size());
        assertEquals(
            Rank.DEFAULT,
            a.options()
                .get(0)
                .rank());
        assertEquals(
            Rank.EQUALLY_VALID,
            a.options()
                .get(1)
                .rank(),
            "the mirror: no objective separates them");
        assertEquals(
            Rank.MOVES_MORE,
            a.options()
                .get(2)
                .rank(),
            "voiding plate moves 40/s instead of 30/s");
        assertTrue(a.complete(), "small chart, the search finishes");
        for (final Alternative option : a.options()) {
            assertEquals(
                1,
                option.externals()
                    .size(),
                "each leaves its surplus in one place");
            assertFalse(
                option.externals()
                    .get(0)
                    .port()
                    .input(),
                "and leaves it rather than importing");
            assertEquals(
                5.0,
                option.externals()
                    .get(0)
                    .ratePerSecond(),
                1e-4);
        }
        assertEquals(
            3,
            a.options()
                .stream()
                .map(
                    o -> o.externals()
                        .get(0)
                        .port())
                .distinct()
                .count(),
            "and each leaves it somewhere different");
    }

    @Test
    void excessChoice_theRejectedOptionIsListedWithItsReason() {
        // Voiding charcoal is not wrong, it just moves more material - a real answer somebody
        // might want, so it must be listed rather than deleted.
        final Alternatives a = alternatives("excess_choice");

        assertEquals(
            2,
            a.options()
                .size());
        assertEquals(
            Rank.DEFAULT,
            a.options()
                .get(0)
                .rank());
        // 10.6/s of charcoal is 0.53 of an oven craft; 530/s of nitrogen is 0.17 of a centrifuge
        // craft. Both are fractions of a craft rather than items against litres, but they are
        // fractions of DIFFERENT machines' crafts - a real comparison, and a debatable one, so the
        // loser is listed instead of deleted.
        assertEquals(
            Rank.VOIDS_MORE,
            a.options()
                .get(1)
                .rank());
        assertEquals(
            530.0,
            a.options()
                .get(0)
                .externals()
                .get(0)
                .ratePerSecond(),
            1e-4,
            "default: nitrogen");
        assertEquals(
            10.6,
            a.options()
                .get(1)
                .externals()
                .get(0)
                .ratePerSecond(),
            1e-4,
            "alternative: charcoal");
    }

    @Test
    void mk1_theSourceSinkTiltIsShownRatherThanHidden() {
        // The 1025/1024 tilt is a taste decision in a constant. It stays the default, but the
        // import it silently discards has to appear in the list.
        final Alternatives a = alternatives("mk1");

        assertEquals(
            2,
            a.options()
                .size());
        assertEquals(
            Rank.DEFAULT,
            a.options()
                .get(0)
                .rank());
        assertEquals(
            Rank.IMPORTS_INSTEAD,
            a.options()
                .get(1)
                .rank());
        assertFalse(
            a.options()
                .get(0)
                .externals()
                .get(0)
                .port()
                .input(),
            "the default leaves a surplus");
        assertTrue(
            a.options()
                .get(1)
                .externals()
                .get(0)
                .port()
                .input(),
            "the alternative imports");
    }

    @Test
    void everyChartEnumeratesWithinBudget_andAdmitsWhenItStoppedEarly() {
        // The list is opened by a click, so it may cost a beat - but never a hang, and never a
        // claim of completeness it did not earn. palladium_line has ~1100 candidate swaps and is
        // capped at 64; what it skipped has to reach the user.
        for (final String chart : GtnhFlowLoader.CORPUS) {
            final long start = System.currentTimeMillis();
            final Alternatives a = alternatives(chart);
            final long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed < 5_000, () -> chart + " took " + elapsed + "ms to enumerate");
            if (!a.complete()) {
                assertTrue(
                    a.notes()
                        .stream()
                        .anyMatch(
                            n -> n.message() == SolverMessage.STOPPED_EARLY
                                || n.message() == SolverMessage.SHOWING_CLOSEST),
                    () -> chart + " truncated its list without saying so: " + a.notes());
            }
            // One current answer per decision, not one for the chart: a chart with three open
            // gates is asking three questions and each needs its own "this is what it does now".
            for (final var decision : a.options()
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(Alternative::replaces))
                .entrySet()) {
                assertEquals(
                    1,
                    decision.getValue()
                        .stream()
                        .filter(o -> o.rank() == Rank.DEFAULT)
                        .count(),
                    () -> chart + " decision " + decision.getKey() + " must have exactly one current answer");
            }
        }
    }

    @Test
    void loopGraph_theRejectedAcidSourceIsStillOnOffer() {
        // The hand-derived answer sources diluted acid at the DT. Sourcing sulfuric at the LCR
        // instead is what stage 3 rejected on internal flow - a real preference, so it is listed.
        final Alternatives a = alternatives("loopGraph");

        assertEquals(
            2,
            a.options()
                .size());
        assertEquals(
            Rank.DEFAULT,
            a.options()
                .get(0)
                .rank());
        assertTrue(
            a.options()
                .get(1)
                .externals()
                .get(0)
                .port()
                .input(),
            "the alternative is also an import");
        assertTrue(
            a.options()
                .get(1)
                .rank() == Rank.MOVES_MORE
                || a.options()
                    .get(1)
                    .rank() == Rank.VOIDS_MORE,
            () -> "and it lost on a comparison, not a coin flip: " + a.options()
                .get(1)
                .rank());
    }

    @Test
    void unambiguousCharts_askNothing() {
        // Nothing crosses the boundary at a wired port, so there is no decision to present - not
        // even a one-row list saying so, which would be a question with no question in it.
        for (final String chart : List.of("light_fuel", "nanocircuits", "light_fuel_hydrogen_loop")) {
            assertTrue(
                alternatives(chart).options()
                    .isEmpty(),
                chart + " balances with no gates");
        }
    }

    @Test
    void aChosenAlternativeIsHonouredAndSurvivesTheSolve() {
        final LoadedChart chart = GtnhFlowLoader.load("symmetric_choice");
        final Alternatives a = alternatives("symmetric_choice");
        final Alternative other = a.options()
            .get(1);

        final Answer picked = Balancer
            .solveWithAlternatives(BalanceMode.AUTO, chart.graph(), false, other.key(), Map.of());
        final Answer.Solved solved = solved(picked);
        assertEquals(other.key(), solved.solution().key, "the chart came back on the chosen support");
        assertEquals(
            other.externals()
                .get(0)
                .port(),
            solved.solution().gatedSinks.get(0)
                .port(),
            "surplus leaves where the user asked");
    }

    @Test
    void aChoiceSurvivesEncodeAndDecode() {
        final LoadedChart chart = GtnhFlowLoader.load("symmetric_choice");
        final Alternatives a = alternatives("symmetric_choice");
        final ChoiceKey picked = a.options()
            .get(1)
            .key();
        // Graph speaks the balancer package's ChoiceKey directly: gate anchors are ports, not gate
        // indices, so the key itself survives a save with nothing in it a rebuild could invalidate.
        chart.graph()
            .setExcessChoice(picked);

        // Only the key is asserted here: Serializer cannot rebuild ports for the harness's
        // Minecraft-free ingredient type, so a reloaded corpus chart has no ports to re-solve
        // against. What matters is that the key itself is node ids and port indices, with nothing
        // in it that a rebuild could invalidate.
        final Graph reloaded = Serializer.decodeGraph(Serializer.encodeGraph(chart.graph()));
        assertEquals(picked, reloaded.getExcessChoice(), "gate anchors are ports, so they survive a save");
    }

    @Test
    void aChoiceThatNoLongerFitsFallsBackWithANote() {
        final LoadedChart chart = GtnhFlowLoader.load("symmetric_choice");
        final Alternatives a = alternatives("symmetric_choice");
        // A key from a different chart names ports this one does not have.
        final Alternatives other = alternatives("mk1");

        final Answer r = Balancer
            .solveWithAlternatives(BalanceMode.AUTO, chart.graph(), false, other.chosen(), Map.of());
        final Answer.Solved solved = solved(r);
        assertEquals(a.chosen(), solved.solution().key, "fell back to the solver's own answer");
        assertTrue(
            solved.solution().notes.stream()
                .anyMatch(
                    n -> n.message() == SolverMessage.CHOICE_NO_LONGER_FITS
                        || n.message() == SolverMessage.CHOICE_NEEDS_MORE_GATES),
            () -> "and said so: " + solved.solution().notes);
    }

    private static Answer.Solved solved(final Answer answer) {
        if (answer instanceof final Answer.Solved solved) return solved;
        throw new AssertionError("solve failed: " + describe(answer));
    }

    private static String describe(final Answer answer) {
        return answer instanceof final Answer.Failed failed ? failed.failure().describe() : answer.toString();
    }
}
