package com.sbancuz.plannh.harness;

import java.util.LinkedHashMap;
import java.util.Map;

import com.sbancuz.plannh.data.flowchart.Balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.Balancer.NodeBalance;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Node;

/**
 * Independent conservation check over a balance result - never trust a solver's status code:
 * every solution is validated against the graph itself.
 *
 * <p>
 * For each ingredient that is both produced and consumed across a drawn edge, the summed
 * per-second production must match the summed per-second consumption. Ports with no edges are
 * terminals (free externals) and are exempt. Returns per-ingredient residuals (positive =
 * overproduction).
 */
public final class ConservationValidator {

    private ConservationValidator() {}

    public static Map<String, Double> residuals(final GtnhFlowLoader.LoadedChart chart, final BalanceResult result) {
        final Map<String, Double> produced = new LinkedHashMap<>();
        final Map<String, Double> consumed = new LinkedHashMap<>();

        // Only ports touched by an edge participate; unconnected ports are terminals.
        for (final Edge edge : chart.graph()
            .getEdges()) {
            final Node src = chart.graph().nodes.get(edge.sourceNodeId);
            final Node dst = chart.graph().nodes.get(edge.targetNodeId);
            final String ingredient = TestIngredients.nameOf(src.outputs.get(edge.sourceOutputIndex));
            produced.putIfAbsent(ingredient, 0.0);
            consumed.putIfAbsent(ingredient, 0.0);
        }

        for (final Node node : chart.machines()) {
            final NodeBalance nb = result.nodeBalances()
                .get(node.id);
            if (nb == null) continue;
            final double perSecond = 20.0 / Math.max(1, nb.durationPerOp());
            for (final Map.Entry<Integer, Float> out : nb.effectiveOutputs()
                .entrySet()) {
                final String ingredient = TestIngredients.nameOf(node.outputs.get(out.getKey()));
                if (produced.containsKey(ingredient) && isConnectedOutput(chart, node, out.getKey())) {
                    produced.merge(ingredient, out.getValue() * perSecond, Double::sum);
                }
            }
            for (final Map.Entry<Integer, Float> in : nb.effectiveInputs()
                .entrySet()) {
                final String ingredient = TestIngredients.nameOf(node.inputs.get(in.getKey()));
                if (consumed.containsKey(ingredient) && isConnectedInput(chart, node, in.getKey())) {
                    consumed.merge(ingredient, in.getValue() * perSecond, Double::sum);
                }
            }
        }

        final Map<String, Double> residuals = new LinkedHashMap<>();
        for (final String ingredient : produced.keySet()) {
            residuals.put(ingredient, produced.get(ingredient) - consumed.getOrDefault(ingredient, 0.0));
        }
        return residuals;
    }

    private static boolean isConnectedOutput(final GtnhFlowLoader.LoadedChart chart, final Node node, final int port) {
        for (final Edge edge : chart.graph()
            .getEdges()) {
            if (edge.sourceNodeId.equals(node.id) && edge.sourceOutputIndex == port) return true;
        }
        return false;
    }

    private static boolean isConnectedInput(final GtnhFlowLoader.LoadedChart chart, final Node node, final int port) {
        for (final Edge edge : chart.graph()
            .getEdges()) {
            if (edge.targetNodeId.equals(node.id) && edge.targetInputIndex == port) return true;
        }
        return false;
    }
}
