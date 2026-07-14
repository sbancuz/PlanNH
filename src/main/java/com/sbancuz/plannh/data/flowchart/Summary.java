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

    public static Summary compute(final BalanceResult balance, final Graph graph) {
        final Map<LineKey, Float> outputMap = new HashMap<>();
        final Map<LineKey, Float> inputMap = new HashMap<>();
        final Map<LineKey, Float> propertyMap = new HashMap<>();

        // Pass 1: accumulate all node outputs before any netting.
        for (final Node node : graph.getNodes()
            .values()) {
            final var nb = balance.nodeBalances()
                .get(node.id);
            if (nb == null) continue;

            for (int i = 0; i < node.getOutputs()
                .size(); i++) {
                final Float total = nb.effectiveOutputs.get(i);
                if (total == null || total <= 0) continue;
                outputMap.merge(
                    LineKey.ResourceKey.of(
                        node.getOutputs()
                            .get(i)),
                    total,
                    Float::sum);
            }
        }

        // Pass 2: net inputs against the fully-populated output map.
        for (final Node node : graph.getNodes()
            .values()) {
            final var nb = balance.nodeBalances()
                .get(node.id);
            if (nb == null) continue;

            for (int i = 0; i < node.getInputs()
                .size(); i++) {
                final Float total = nb.effectiveInputs.get(i);
                if (total == null || total <= 0) continue;
                final LineKey key = LineKey.ResourceKey.of(
                    node.getInputs()
                        .get(i));
                final float existing = outputMap.getOrDefault(key, 0f);
                final float consumed = Math.min(existing, total);
                if (consumed > 0) {
                    final float remaining = existing - consumed;
                    if (remaining > 0) outputMap.put(key, remaining);
                    else outputMap.remove(key);
                }
                final float deficit = total - consumed;
                if (deficit > 0) inputMap.merge(key, deficit, Float::sum);
            }
        }

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
