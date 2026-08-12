package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.data.flowchart.Summary.Line;
import com.sbancuz.plannh.harness.GtnhFlowLoader;

/**
 * The summary's two lists are read for opposite things - the headline product against the scarcest
 * ingredient - so they sort opposite ways. They also used to come out of a hash map, which meant
 * two draws of one unedited chart could disagree about the order.
 */
class SummaryOrderTest {

    private static final List<String> CHARTS = List.of("mk1", "loopGraph", "excess_choice", "mk1_tiberium");

    @Test
    void outputsLeadWithTheBiggest() {
        for (final String name : CHARTS) {
            assertSorted(name, "outputs", summaryOf(name).outputs(), false);
        }
    }

    @Test
    void inputsLeadWithTheSmallest() {
        for (final String name : CHARTS) {
            assertSorted(name, "inputs", summaryOf(name).inputs(), true);
        }
    }

    @Test
    void oneUneditedChartSummarisesTheSameWayTwice() {
        for (final String name : CHARTS) {
            final var graph = GtnhFlowLoader.load(name)
                .graph();
            assertTrue(
                names(
                    graph.summary()
                        .outputs()).equals(
                            names(
                                graph.summary()
                                    .outputs())),
                () -> name + " reordered its outputs between two reads");
        }
    }

    private static Summary summaryOf(final String name) {
        return GtnhFlowLoader.load(name)
            .graph()
            .summary();
    }

    private static List<String> names(final List<Line<?>> lines) {
        return lines.stream()
            .map(Line::displayName)
            .toList();
    }

    private static void assertSorted(final String chart, final String what, final List<Line<?>> lines,
        final boolean ascending) {
        for (int i = 1; i < lines.size(); i++) {
            final float previous = lines.get(i - 1)
                .amount();
            final float current = lines.get(i)
                .amount();
            final int index = i;
            assertTrue(
                ascending ? current >= previous : current <= previous,
                () -> chart + " " + what + " out of order at row " + index + ": " + previous + " then " + current);
        }
    }
}
