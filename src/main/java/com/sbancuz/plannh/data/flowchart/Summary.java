package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipeResource;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceResult;

public record Summary(List<Line<?>> outputs, List<Line<?>> inputs, List<Line<?>> properties) {

    public enum SummaryMode {
        CYCLES,
        THROUGHPUT
    }

    public record Line<T> (RecipeProperty<T> label, T resource, float amount) {

        public String displayName() {
            return label.formatDisplayName(resource);
        }

        public String displayAmount(float amount) {
            return label.formatAmount(amount);
        }

    }

    @SuppressWarnings("rawtypes")
    private sealed interface LineKey permits LineKey.ResourceKey,LineKey.PropertyKey {

        record ResourceKey<T> (RecipeResource<T> type, T resource) implements LineKey {

            @SuppressWarnings("unchecked")
            static ResourceKey<Object> of(final Port port) {
                return new ResourceKey<>((RecipeResource<Object>) port.getType(), port.getValue());
            }

            Line<?> toLine(final float amount) {
                return new Line<>(type, resource, amount);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean equals(final Object o) {
                return o instanceof ResourceKey<?>(RecipeResource<?> type1, Object resource1)
                    && type == type1
                    && type.canConnect(resource, (T) resource1);
            }

            @Override
            public int hashCode() {
                return type.hashValue(resource);
            }
        }

        record PropertyKey(RecipeProperty<?> prop) implements LineKey {}
    }

    public static Summary compute(final BalanceResult balance, final Graph graph, final boolean opsMode) {
        // In ops mode, effective amounts are per-operation totals (no duration scaling needed).
        final int cycleTicks;
        if (opsMode) {
            cycleTicks = 1;
        } else {
            int maxTicks = 0;
            for (final Node node : graph.getNodes()) {
                final var nb = balance.nodeBalances()
                    .get(node.id);
                if (nb != null) maxTicks = Math.max(maxTicks, nb.durationPerOp());
            }
            cycleTicks = maxTicks > 0 ? maxTicks : 20;
        }

        // Accumulate scaled outputs and inputs per resource across all ports (both connected and unconnected).
        final Map<LineKey, Float> outputMap = new HashMap<>();
        final Map<LineKey, Float> inputMap = new HashMap<>();
        final Map<LineKey, Float> propertyMap = new HashMap<>();

        for (final Node node : graph.getNodes()) {
            final var nb = balance.nodeBalances()
                .get(node.id);
            if (nb == null) continue;

            final float scale = opsMode ? 1f : (float) cycleTicks / Math.max(1, nb.durationPerOp());

            for (int i = 0; i < node.outputs.size(); i++) {
                final Float total = nb.effectiveOutputs()
                    .get(i);
                if (total == null || total <= 0) continue;
                outputMap.merge(LineKey.ResourceKey.of(node.outputs.get(i)), total * scale, Float::sum);
            }

            for (int i = 0; i < node.inputs.size(); i++) {
                final Float total = nb.effectiveInputs()
                    .get(i);
                if (total == null || total <= 0) continue;
                inputMap.merge(LineKey.ResourceKey.of(node.inputs.get(i)), total * scale, Float::sum);
            }
        }

        // Net by resource: output = max(0, prod - cons), input = max(0, cons - prod).
        final Map<LineKey, Float> netInputs = new HashMap<>();
        for (final var entry : inputMap.entrySet()) {
            final LineKey key = entry.getKey();
            final float cons = entry.getValue();
            final float prod = outputMap.getOrDefault(key, 0f);
            if (cons > prod) {
                netInputs.put(key, cons - prod);
                outputMap.remove(key);
            } else if (cons == prod) {
                outputMap.remove(key);
            } else {
                outputMap.put(key, prod - cons);
            }
        }
        inputMap.clear();
        inputMap.putAll(netInputs);

        for (final var entry : balance.propertyTotals()
            .entrySet()) {
            propertyMap.merge(new LineKey.PropertyKey(entry.getKey()), (float) entry.getValue(), Float::sum);
        }

        return new Summary(flatten(outputMap), flatten(inputMap), flatten(propertyMap));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Line<?>> flatten(final Map<LineKey, Float> map) {
        final List<Line<?>> result = new ArrayList<>();
        for (final var entry : map.entrySet()) {
            if (entry.getValue() <= 0) continue;
            final Line<?> line = switch (entry.getKey()) {
                case LineKey.ResourceKey rk -> rk.toLine(entry.getValue());
                case LineKey.PropertyKey pk   -> new Line(pk.prop(), pk.prop().getDefaultValue(), entry.getValue());
            };
            result.add(line);
        }
        return result;
    }
}
