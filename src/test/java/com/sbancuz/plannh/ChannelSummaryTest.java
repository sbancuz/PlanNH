package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.channels.ChannelProblem;
import com.sbancuz.plannh.data.channels.ChannelReport;
import com.sbancuz.plannh.data.channels.ChannelSolver;
import com.sbancuz.plannh.data.channels.ChannelSolver.Mode;
import com.sbancuz.plannh.data.channels.Ingredient;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.data.flowchart.Summary.Section;

/** The summary side of the channel analysis. */
class ChannelSummaryTest {

    @Test
    void channelsSitBeforeMessagesByDefault() {
        final List<Section> order = Arrays.stream(new Summary().getSectionOrder())
            .mapToObj(i -> Section.VALUES[i])
            .toList();
        assertEquals(order.indexOf(Section.MESSAGES) - 1, order.indexOf(Section.CHANNELS));
        assertEquals(Section.VALUES.length, order.size());
    }

    @Test
    void theSectionIsHiddenWithoutAnAnalyzer() {
        final ChannelReport.Analyzer installed = ChannelReport.analyzer();
        ChannelReport.setAnalyzer(null);
        try {
            final Summary summary = new Summary();
            summary.setChannelsEnabled(true);
            summary.recompute(new Graph());
            assertTrue(
                summary.lines(Section.CHANNELS)
                    .isEmpty());
        } finally {
            ChannelReport.setAnalyzer(installed);
        }
    }

    @Test
    void priorityPicksTheCheaperOrderedLayout() {
        // No conflicts: circuit order fits both in one bus, colored buses need two
        assertEquals(
            Mode.CIRCUIT,
            report(List.of()).solution(true)
                .mode());
        // #2 is a plan recipe that can take #1's batch: circuit order can't put it second, so it needs
        // two channels (one machine, two bus and hatch groups); colored buses order it in one
        assertEquals(
            Mode.COLOR,
            report(List.of(new ChannelProblem.BatchConflict(Set.of(circuit(2)), true, 1))).solution(true)
                .mode());
        assertEquals(
            Mode.NONE,
            report(List.of()).solution(false)
                .mode());
    }

    private static Ingredient.Item circuit(final int n) {
        return new Ingredient.Item("circuit", n, null);
    }

    /** Two one-fluid circuit recipes, #1 open to {@code conflicts}. */
    private static ChannelReport.MachineReport report(final List<ChannelProblem.BatchConflict> conflicts) {
        final List<ChannelProblem.Recipe> recipes = List.of(
            new ChannelProblem.Recipe(Set.of(circuit(1)), Set.of(new Ingredient.Fluid("a"))),
            new ChannelProblem.Recipe(Set.of(circuit(2)), Set.of(new Ingredient.Fluid("b"))));
        final ChannelProblem problem = ChannelProblem
            .batch(recipes, List.of(conflicts, List.of()), Comparator.comparingInt(Ingredient.Item::meta));
        final Map<Mode, ChannelSolver.Solution> solutions = new EnumMap<>(Mode.class);
        for (final Mode mode : Mode.values()) solutions.put(mode, ChannelSolver.solve(problem, mode));
        return new ChannelReport.MachineReport("handler", "LCR", null, 0, List.of(), Map.of(), solutions, List.of(), 2);
    }
}
