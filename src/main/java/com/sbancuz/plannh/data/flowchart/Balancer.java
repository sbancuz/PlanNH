package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.util.StatCollector;

import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import com.gtnewhorizons.angelica.shadow.javax.annotation.Nonnull;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeProperty;

public final class Balancer {

    public enum BalanceMode {

        NONE,
        /** Output-priority: all production must be shipped, inputs get at least what they need. */
        OUTPUT,
        /** Input-priority: inputs consume exactly their capacity, outputs supply at least what's demanded. */
        INPUT;

        public String displayName() {
            return StatCollector.translateToLocal(
                "plannh.gui.balancer_mode." + this.name()
                    .toLowerCase());
        }
    }

    @Nonnull
    public static BalanceResult balance(final Graph graph, final BalanceMode mode, final boolean opsMode) {
        return switch (mode) {
            case NONE -> balanceNone(graph);
            case OUTPUT, INPUT -> solveILPOrFallback(graph, mode, opsMode);
        };
    }

    @Nonnull
    private static BalanceResult solveILPOrFallback(final Graph graph, final BalanceMode mode, final boolean opsMode) {
        final Map<UUID, Integer> ilpOps = balanceILP(graph, mode, opsMode);
        if (ilpOps != null) {
            return buildResult(graph, ilpOps);
        }
        return balanceNone(graph);
    }

    @Nonnull
    private static BalanceResult balanceNone(final Graph graph) {
        final Map<UUID, Integer> ops = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            ops.put(node.id, node.machineConfig.getMachineCount());
        }
        return buildResult(graph, ops);
    }

    @Nonnull
    static BalanceResult buildResult(final Graph graph, final Map<UUID, Integer> ops) {
        final Map<UUID, NodeBalance> nodeBalances = new HashMap<>();
        final Map<RecipeProperty<?>, Long> propertyTotals = new HashMap<>();
        int totalOps = 0;
        int totalDuration = 0;

        for (final Node node : graph.getNodes()) {
            final int opCount = ops.get(node.id);
            totalOps += opCount;

            final MachineConfig cfg = node.machineConfig;
            final var eff = cfg.computeEffect(node.properties, node.durationTicks);
            final long eutPerOp = eff.energyPerT();
            final int durPerOp = eff.durationTicks();
            final int throughputFactor = eff.throughputFactor();

            final long totalEnergy = eutPerOp * durPerOp * opCount;
            if (durPerOp > totalDuration) totalDuration = durPerOp;

            final Map<Integer, Float> effOuts = new HashMap<>(node.outputs.size());
            for (int i = 0; i < node.outputs.size(); i++) {
                final var outStack = node.outputs.get(i);
                final int stackSize = outStack.getAmount();
                if (stackSize <= 0) continue;
                final float total = opCount * stackSize * outStack.getChance() * cfg.outputMultiplier(i) * throughputFactor;
                if (total <= 0) continue;
                effOuts.put(i, total);
            }

            final Map<Integer, Float> effIns = new HashMap<>(node.inputs.size());
            for (int i = 0; i < node.inputs.size(); i++) {
                final var inStack = node.inputs.get(i);
                final int stackSize = inStack.getAmount();
                if (stackSize <= 0) continue;
                final float total = opCount * stackSize * inStack.getChance() * cfg.inputMultiplier(i) * throughputFactor;
                if (total <= 0) continue;
                effIns.put(i, total);
            }

            nodeBalances.put(node.id, new NodeBalance(opCount, durPerOp, totalEnergy, durPerOp, effOuts, effIns));

            for (final Map.Entry<RecipeProperty<?>, Object> entry : node.properties.entrySet()) {
                if (entry.getValue() instanceof final Number num) {
                    propertyTotals.merge(entry.getKey(), num.longValue() * opCount, Long::sum);
                }
            }
        }

        return new BalanceResult(nodeBalances, propertyTotals, totalOps, totalDuration);
    }

    public record NodeBalance(int operations, int totalDurationTicks, long totalEnergy, int durationPerOp,
        Map<Integer, Float> effectiveOutputs, Map<Integer, Float> effectiveInputs) {}

    public record BalanceResult(Map<UUID, NodeBalance> nodeBalances, Map<RecipeProperty<?>, Long> propertyTotals,
        int totalOperations, int totalDurationTicks) {}

    /**
     * Solves the optimal machine counts via a continuous LP relaxation, then rounds each
     * count up to the nearest integer to guarantee throughput.
     *
     * Sets up a linear program where:
     * <ul>
     * <li>Each node has a variable {@code o_i} = number of machines (operations)</li>
     * <li>Each edge has a variable {@code f_e} = throughput on that connection</li>
     * <li>Objective: minimise {@code Σ o_i} (total machine count)</li>
     * <li>Edge capacity: {@code f_e ≤ o_src * srcCap}, {@code f_e ≤ o_tgt * tgtCap}</li>
     * </ul>
     * In <b>output mode</b> ({@code FIXED}):
     * <ul>
     * <li>{@code Σ f = o_i * srcCap} for each output port — all production must be shipped</li>
     * <li>{@code Σ f ≥ o_j * tgtCap} for each input port — machines must get enough input</li>
     * </ul>
     * In <b>input mode</b> ({@code INPUT}):
     * <ul>
     * <li>{@code Σ f = o_j * tgtCap} for each input port — exact consumption</li>
     * <li>{@code Σ f ≥ o_i * srcCap} for each output port — supply at least what's demanded</li>
     * </ul>
     * When {@code opsMode} is {@code true}, capacities are per-operation
     * (items per operation, not items per tick) — duration is ignored.
     *
     * @param mode    the balance mode (FIXED or INPUT)
     * @param opsMode whether to use per-operation capacities instead of per-tick
     * @return machine counts per node, or {@code null} if the model is infeasible
     */
    private static Map<UUID, Integer> balanceILP(final Graph graph, final BalanceMode mode, final boolean opsMode) {
        // Port-to-edge lookup tables for the constraint loops
        final Map<UUID, Map<Integer, List<Edge>>> srcPortEdges = new HashMap<>();
        final Map<UUID, Map<Integer, List<Edge>>> tgtPortEdges = new HashMap<>();

        for (final Node node : graph.getNodes()) {
            srcPortEdges.put(node.id, new HashMap<>());
            tgtPortEdges.put(node.id, new HashMap<>());
        }

        for (final Edge edge : graph.getEdges()) {
            srcPortEdges.get(edge.sourceNodeId)
                .computeIfAbsent(edge.sourceOutputIndex, k -> new ArrayList<>())
                .add(edge);
            tgtPortEdges.get(edge.targetNodeId)
                .computeIfAbsent(edge.targetInputIndex, k -> new ArrayList<>())
                .add(edge);
        }

        // Per-port capacity: items per tick (throughput) or items per operation
        final Map<UUID, Map<Integer, Float>> srcCaps = new HashMap<>();
        final Map<UUID, Map<Integer, Float>> tgtCaps = new HashMap<>();

        for (final Node node : graph.getNodes()) {
            final MachineConfig cfg = node.machineConfig;
            final var eff = cfg.computeEffect(node.properties, node.durationTicks);
            final int durTicks = Math.max(1, eff.durationTicks());
            final float div = opsMode ? 1f : (float) durTicks;
            final int tf = eff.throughputFactor();

            final Map<Integer, Float> sc = new HashMap<>(node.outputs.size());
            for (int i = 0; i < node.outputs.size(); i++) {
                final var outStack = node.outputs.get(i);
                final float cap = outStack.getAmount() * outStack.getChance() * cfg.outputMultiplier(i) * tf / div;
                if (cap > 0) sc.put(i, cap);
            }
            srcCaps.put(node.id, sc);

            final Map<Integer, Float> tc = new HashMap<>(node.inputs.size());
            for (int i = 0; i < node.inputs.size(); i++) {
                final var inStack = node.inputs.get(i);
                final float cap = inStack.getAmount() * inStack.getChance() * cfg.inputMultiplier(i) * tf / div;
                if (cap > 0) tc.put(i, cap);
            }
            tgtCaps.put(node.id, tc);
        }

        final ExpressionsBasedModel model = new ExpressionsBasedModel();

        final Map<UUID, Variable> opsVars = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            final Variable v = model.addVariable("ops_" + node.id);
            if (node.isMachineCountFixed()) {
                v.lower(node.machineConfig.getMachineCount());
                v.upper(node.machineConfig.getMachineCount());
            } else {
                v.lower(1);
            }
            v.weight(1.0);
            opsVars.put(node.id, v);
        }

        final Map<UUID, Variable> flowVars = new HashMap<>();
        for (final Edge edge : graph.getEdges()) {
            final Variable v = model.addVariable("flow_" + edge.id);
            v.lower(0);
            flowVars.put(edge.id, v);
        }

        if (mode == BalanceMode.INPUT) {
            addPortConstraints(model, opsVars, flowVars, tgtPortEdges, tgtCaps, "in_", true);
            addPortConstraints(model, opsVars, flowVars, srcPortEdges, srcCaps, "out_", false);
        } else {
            addPortConstraints(model, opsVars, flowVars, srcPortEdges, srcCaps, "out_", true);
            addPortConstraints(model, opsVars, flowVars, tgtPortEdges, tgtCaps, "in_", false);
        }

        final Optimisation.Result result = model.minimise();
        if (!result.getState()
            .isFeasible()) {
            return null;
        }

        // Extract machine counts (rounding up to guarantee throughput) and write them back
        // to the node configs so they're available when balancing is later switched off.
        final Map<UUID, Integer> answer = new HashMap<>();
        for (final Node node : graph.getNodes()) {
            final Number raw = opsVars.get(node.id)
                .getValue();
            if (raw == null) return null;
            final int count = (int) Math.ceil(raw.doubleValue() - 1e-6);
            answer.put(node.id, count);
            node.machineConfig.setMachineCount(count);
        }

        return answer;
    }

    /**
     * Adds, for every (node, port) pair that has edges and a known capacity, either an exact
     * flow constraint ({@code Σf = o * cap}, when {@code exact} is {@code true}) or a minimum
     * flow constraint ({@code Σf ≥ o * cap}, otherwise).
     */
    private static void addPortConstraints(final ExpressionsBasedModel model, final Map<UUID, Variable> opsVars,
        final Map<UUID, Variable> flowVars, final Map<UUID, Map<Integer, List<Edge>>> portEdgesByNode,
        final Map<UUID, Map<Integer, Float>> capsByNode, final String exprPrefix, final boolean exact) {

        for (final Map.Entry<UUID, Map<Integer, List<Edge>>> nodeEntry : portEdgesByNode.entrySet()) {
            final UUID nodeId = nodeEntry.getKey();
            final Variable oVar = opsVars.get(nodeId);
            final Map<Integer, Float> caps = capsByNode.get(nodeId);

            for (final Map.Entry<Integer, List<Edge>> portEntry : nodeEntry.getValue()
                .entrySet()) {
                final List<Edge> edges = portEntry.getValue();
                final Float cap = caps.get(portEntry.getKey());
                if (cap == null || edges.isEmpty()) continue;

                final Expression expr = model.addExpression(exprPrefix + nodeId + "_" + portEntry.getKey());
                for (final Edge e : edges) {
                    expr.set(flowVars.get(e.id), 1.0);
                }
                expr.set(oVar, -cap);
                if (exact) {
                    expr.level(0);
                } else {
                    expr.lower(0);
                }
            }
        }
    }
}
