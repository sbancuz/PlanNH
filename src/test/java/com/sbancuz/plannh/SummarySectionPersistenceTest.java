package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Summary.SummarySection;
import com.sbancuz.plannh.data.serialization.Serializer;

/**
 * Folded summary sections are a per-chart preference: losing them re-expands panels every launch,
 * and sharing them between slots followed one unfolded panel into every chart the user opened next.
 */
class SummarySectionPersistenceTest {

    private static Plan twoSlots() {
        final Plan plan = Plan.createEmpty();
        plan.getGraphs()
            .add(new Graph("Slot 2"));
        return plan;
    }

    @Test
    void foldedSectionsSurviveEncodeDecodePerSlot() {
        final Plan plan = twoSlots();
        plan.getGraphs()
            .get(0).collapsedSummarySections.clear();
        plan.getGraphs()
            .get(0).collapsedSummarySections.add(SummarySection.MESSAGES);
        plan.getGraphs()
            .get(1).collapsedSummarySections.clear();

        final Plan decoded = Serializer.decodePlan(Serializer.encodePlan(plan));

        assertEquals(
            EnumSet.of(SummarySection.MESSAGES),
            decoded.getGraphs()
                .get(0).collapsedSummarySections,
            "one chart's folds");
        assertEquals(
            EnumSet.noneOf(SummarySection.class),
            decoded.getGraphs()
                .get(1).collapsedSummarySections,
            "an explicitly empty set is 'everything open', not 'never saved'");
    }

    @Test
    void aNewSlotStartsFromTheDefaultsRatherThanTheLastChartsPanel() {
        final Plan plan = twoSlots();
        plan.getGraphs()
            .get(0).collapsedSummarySections.clear();

        plan.getGraphs()
            .add(new Graph("Slot 3"));

        assertEquals(
            Graph.defaultSummaryFolds(),
            plan.getGraphs()
                .get(2).collapsedSummarySections);
        assertEquals(
            Graph.defaultSummaryFolds(),
            Serializer.decodePlan(Serializer.encodePlan(plan))
                .getGraphs()
                .get(2).collapsedSummarySections,
            "and still does after a round trip");
    }

    /** Saves written before folds were per slot open every chart the way a fresh install would. */
    @Test
    void savesWithoutTheKeyKeepTheDefaults() {
        final String json = Serializer.encodePlan(twoSlots())
            .replace("\"sectionFolds\"", "\"unusedKey\"");

        final Plan decoded = Serializer.decodePlan(json);

        for (final Graph graph : decoded.getGraphs()) {
            assertEquals(Graph.defaultSummaryFolds(), graph.collapsedSummarySections);
        }
    }

    /**
     * The reason the folds are stored section by section: a save written before a section existed
     * must not drag that section open, or every new section arrives expanded for existing users.
     */
    @Test
    void aSectionTheSaveNeverHeardOfKeepsItsDefault() {
        final Plan plan = twoSlots();
        for (final Graph graph : plan.getGraphs()) {
            graph.collapsedSummarySections.clear();
            graph.collapsedSummarySections.add(SummarySection.STATISTICS);
        }
        final String json = Serializer.encodePlan(plan)
            .replace("\"" + SummarySection.HELP.name() + "\"", "\"SECTION_FROM_A_LATER_BUILD\"");

        final Plan decoded = Serializer.decodePlan(json);

        assertEquals(
            EnumSet.of(SummarySection.STATISTICS, SummarySection.HELP),
            decoded.getGraphs()
                .get(0).collapsedSummarySections,
            "the unmentioned section keeps the default fold, the mentioned ones keep the save's");
    }

    /** Solver messages are the one section that starts open: a failed balance must not be silent. */
    @Test
    void solverMessagesStartOpen() {
        assertEquals(
            EnumSet.of(SummarySection.MACHINE_COUNTS, SummarySection.STATISTICS, SummarySection.HELP),
            Graph.defaultSummaryFolds());
    }
}
