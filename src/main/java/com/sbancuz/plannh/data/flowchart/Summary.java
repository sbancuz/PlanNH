package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipeResource;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceResult;

public record Summary(List<Line<?>> outputs, List<Line<?>> inputs, List<Line<?>> properties) {

    public enum SummaryMode {
        CYCLES,
        THROUGHPUT
    }

    public record Line<T> (RecipeProperty<T> label, T resource, float amount, boolean perSecond) {

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

            Line<?> toLine(final float amount, final boolean perSecond) {
                return new Line<>(type, resource, amount, perSecond);
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
        final Set<LineKey> stepSourcedOutputs = new HashSet<>();
        final Set<LineKey> stepSourcedInputs = new HashSet<>();

        // Pass 1: accumulate the outputs of every participant (machines + steps)
        // before any netting. Steps contribute their raw amount, with no scaling.
        for (final FlowData participant : graph.getFlowParticipants()) {
            final List<Port<?>> outs = participant.getOutputs();
            final Map<Integer, Float> effOuts = participant.effectiveOutputs(balance);
            for (int i = 0; i < outs.size(); i++) {
                final Float total = effOuts.get(i);
                if (total == null || total <= 0) continue;
                final LineKey key = LineKey.ResourceKey.of(outs.get(i));
                outputMap.merge(key, total, Float::sum);
                if (participant instanceof Step) stepSourcedOutputs.add(key);
            }
        }

        // Pass 2: net inputs against the fully-populated output map.
        for (final FlowData participant : graph.getFlowParticipants()) {
            final List<Port<?>> ins = participant.getInputs();
            final Map<Integer, Float> effIns = participant.effectiveInputs(balance);
            for (int i = 0; i < ins.size(); i++) {
                final Float total = effIns.get(i);
                if (total == null || total <= 0) continue;
                final LineKey key = LineKey.ResourceKey.of(ins.get(i));
                if (participant instanceof Step) stepSourcedInputs.add(key);
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

        return new Summary(
            flatten(outputMap, stepSourcedOutputs),
            flatten(inputMap, stepSourcedInputs),
            flatten(propertyMap, Set.of()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Line<?>> flatten(final Map<LineKey, Float> map, final Set<LineKey> stepSourced) {
        final List<Line<?>> result = new ArrayList<>();
        for (final var entry : map.entrySet()) {
            if (entry.getValue() <= 0) continue;
            final boolean perSecond = stepSourced.contains(entry.getKey());
            final Line<?> line = switch (entry.getKey()) {
                case LineKey.ResourceKey rk -> rk.toLine(entry.getValue(), perSecond);
                case LineKey.PropertyKey pk -> new Line(pk.prop(), pk.prop().getDefaultValue(), entry.getValue(), false);
            };
            result.add(line);
        }
        return result;
    }
}
