package com.sbancuz.plannh.harness;

import java.util.LinkedHashMap;
import java.util.Map;

import com.sbancuz.plannh.data.flowchart.Balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.Balancer.NodeBalance;
import com.sbancuz.plannh.data.flowchart.Node;

/**
 * Independent aggregation over a balance result: per-ingredient production and consumption,
 * summed across every port and normalized to the balance's common cycle. This is the same
 * accounting the summary widget displays, recomputed from the raw node balances, so tests can
 * assert the summary against it without trusting the summary's own code.
 */
public final class ConservationValidator {

    private ConservationValidator() {}

    /** Per-ingredient totals over one common cycle; the summary shows {@link #net()} lines. */
    public record Flows(double produced, double consumed) {

        public double net() {
            return produced - consumed;
        }
    }

    public static Map<String, Flows> flows(final GtnhFlowLoader.LoadedChart chart, final BalanceResult result) {
        int cycleTicks = 0;
        for (final Node node : chart.machines()) {
            final NodeBalance nb = result.nodeBalances()
                .get(node.id);
            if (nb != null) cycleTicks = Math.max(cycleTicks, nb.durationPerOp());
        }
        if (cycleTicks == 0) cycleTicks = 20;

        final Map<String, Flows> flows = new LinkedHashMap<>();
        for (final Node node : chart.machines()) {
            final NodeBalance nb = result.nodeBalances()
                .get(node.id);
            if (nb == null) continue;
            final double scale = (double) cycleTicks / Math.max(1, nb.durationPerOp());
            for (final Map.Entry<Integer, Float> out : nb.effectiveOutputs()
                .entrySet()) {
                if (out.getValue() <= 0) continue;
                flows.merge(
                    TestIngredients.nameOf(node.outputs.get(out.getKey())),
                    new Flows(out.getValue() * scale, 0),
                    (a, b) -> new Flows(a.produced() + b.produced(), a.consumed()));
            }
            for (final Map.Entry<Integer, Float> in : nb.effectiveInputs()
                .entrySet()) {
                if (in.getValue() <= 0) continue;
                flows.merge(
                    TestIngredients.nameOf(node.inputs.get(in.getKey())),
                    new Flows(0, in.getValue() * scale),
                    (a, b) -> new Flows(a.produced(), a.consumed() + b.consumed()));
            }
        }
        return flows;
    }
}
