package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.client.Background;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceView.Kind;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;
import com.sbancuz.plannh.harness.GtnhFlowLoader;

/**
 * The asynchronous half of the balancer: work always leaves the caller for the one background
 * thread, and an answer only becomes visible once it comes back. Headless there is no client tick
 * to publish onto, so the tests below keep asking - as the canvas does - until an answer lands.
 */
class BalanceAsyncTest {

    private static Graph chart(final String name) {
        return GtnhFlowLoader.load(name)
            .graph();
    }

    private static long surplus(final Graph graph) {
        return graph.boundary()
            .stream()
            .filter(b -> b.kind() == Kind.EXCESS)
            .count();
    }

    /** Keeps asking, the way the canvas does each frame, until the chart has an answer. */
    private static void awaitSolved(final Graph graph) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 30_000;
        while (graph.getSolvedAt() != graph.getVersion() && System.currentTimeMillis() < deadline) {
            graph.balance();
            Thread.sleep(1);
        }
        assertEquals(graph.getVersion(), graph.getSolvedAt(), "the solve never landed");
    }

    @Test
    void workLeavesTheThreadThatAskedForIt() throws InterruptedException {
        final Thread caller = Thread.currentThread();
        final List<Thread> ran = new CopyOnWriteArrayList<>();

        Background.offClient(() -> ran.add(Thread.currentThread()), () -> {});

        final long deadline = System.currentTimeMillis() + 30_000;
        while (ran.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(1);

        assertEquals(1, ran.size());
        assertTrue(ran.get(0) != caller, "the work ran on the thread that asked for it");
        assertEquals(
            "plannh-solver",
            ran.get(0)
                .getName());
    }

    @Test
    void aChartReadsItsOwnCountsUntilAnAnswerLands() throws InterruptedException {
        final Graph graph = chart("excess_choice");

        // Not empty: a chart that dropped its answer would read as "nothing crosses the boundary",
        // which is a claim about the solver rather than about the chart.
        assertTrue(
            graph.balance()
                .nodeBalances()
                .size() > 0);
        assertEquals(0, surplus(graph));

        awaitSolved(graph);
        assertEquals(1, surplus(graph));
    }

    @Test
    void anAsynchronousAnswerMatchesTheSynchronousOne() throws InterruptedException {
        final Graph graph = chart("excess_choice");
        graph.balance();
        awaitSolved(graph);

        final BalanceResult expected = Balancer.balance(chart("excess_choice"), BalanceMode.AUTO);

        assertTrue(graph.balance() instanceof BalanceResult.Solved, "the answer is a solved one");
        assertEquals(
            expected.nodeBalances()
                .keySet(),
            graph.balance()
                .nodeBalances()
                .keySet());
        assertEquals(
            expected.totalOperations(),
            graph.balance()
                .totalOperations(),
            1e-6);
    }

    @Test
    void twoChartsOnTheSameVersionAreStillToldApart() throws InterruptedException {
        final Summary summary = Plan.getInstance()
            .getSummary();
        final Graph first = chart("excess_choice");
        first.balance();
        awaitSolved(first);
        summary.recompute(first);

        // A second chart driven onto the version the first one is on. The version alone cannot tell
        // them apart, which is why the panel watches the chart as well.
        final Graph second = chart("mk1");
        while (second.getVersion() < first.getVersion()) second.bumpVersion();
        second.balance();
        awaitSolved(second);
        summary.recompute(second);
        final long shared = summary.calculatedAt();

        summary.recompute(first);

        assertEquals(first.getVersion(), shared, "the two charts are on the same version");
        assertEquals(first, summary.computedFor(), "and the panel is told which one it is showing");
    }

    @Test
    void aStaleRenderDoesNotClaimTheVersionItWasNotDerivedFrom() throws InterruptedException {
        final Graph graph = chart("excess_choice");
        final Summary summary = Plan.getInstance()
            .getSummary();
        graph.balance();
        awaitSolved(graph);
        summary.recompute(graph);

        // An edit, then a render of the answer still on hand. Filed under the version that answer
        // came from, not the chart's current one - so the solve that is about to land is a change
        // the panel can see rather than a rewrite of the version it already shows.
        graph.bumpVersion();
        summary.recompute(graph);
        final long stale = summary.calculatedAt();
        assertTrue(stale < graph.getVersion(), "a stale render must not claim the current version");

        graph.balance();
        awaitSolved(graph);
        assertEquals(graph.getVersion(), summary.calculatedAt(), "and a landed answer moves it on");
    }
}
