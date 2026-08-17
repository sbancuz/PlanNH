package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.properties.ResourceProperty;
import com.sbancuz.plannh.data.properties.SummaryProperty;

public record Summary(List<Line<?>> outputs, List<Line<?>> inputs, List<Line<?>> properties) {

    /**
     * Relative tolerance for "produced and consumed cancel". Float epsilon is ~1.2e-7 and these are
     * sums over many ports, so this sits far enough above it to survive accumulation while staying
     * orders of magnitude below any shortfall worth reporting.
     */
    private static final float NET_EPS = 1e-4f;

    private static final Comparator<Line<?>> BY_AMOUNT = Comparator.comparingDouble((Line<?> l) -> l.amount())
        .thenComparing(Line::displayName);

    public enum SummaryMode {
        CYCLES,
        THROUGHPUT
    }

    /**
     * The summary panel's foldable sections, in the order they are drawn. Only the reference
     * material folds: the choices, inputs and outputs above them ARE the answer the panel exists
     * to give, so they have no fold state to keep and are not listed here.
     */
    public enum SummarySection {
        MACHINE_COUNTS,
        /** Drawn from {@link Summary#properties()}; "Statistics" is what a reader calls them. */
        STATISTICS,
        /** Everything the solver had to say, at every severity. */
        MESSAGES,
        HELP
    }

    public record Line<T> (SummaryProperty<T> label, T resource, float amount) {

        public String displayName() {
            return label.formatDisplayName(resource);
        }

        public String displayAmount(float amount) {
            return label.formatAmount(amount);
        }

    }

    @SuppressWarnings("rawtypes")
    private sealed interface LineKey permits LineKey.ResourceKey,LineKey.PropertyKey {

        record ResourceKey<T> (ResourceProperty<T> type, T resource) implements LineKey {

            @SuppressWarnings("unchecked")
            static ResourceKey<Object> of(final Port port) {
                return new ResourceKey<>((ResourceProperty<Object>) port.getType(), port.getValue());
            }

            Line<?> toLine(final float amount) {
                return new Line<>(type, resource, amount);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean equals(final Object o) {
                return o instanceof ResourceKey<?>(SummaryProperty<?> type1, Object resource1)
                    && type == type1;
//                    && type.canConnect(resource, (T) resource1);
            }

            @Override
            public int hashCode() {
                return type.hashValue(resource);
            }
        }

        record PropertyKey(SummaryProperty<?> prop) implements LineKey {}
    }

    public static Summary compute(final BalanceResult balance, final Graph graph, final boolean opsMode) {
        // In ops mode, effective amounts are per-operation totals (no duration scaling needed).
        final int cycleTicks;
        if (opsMode) {
            cycleTicks = 1;
        } else {
            int maxTicks = 0;
            for (final Node node : graph.getNodes()
                .values()) {
                final var nb = balance.nodeBalances()
                    .get(node.getId());
                if (nb != null) maxTicks = Math.max(maxTicks, nb.durationPerOp());
            }
            cycleTicks = maxTicks > 0 ? maxTicks : 20;
        }

        // Accumulate scaled outputs and inputs per resource across all ports (both connected and unconnected).
        final Map<LineKey, Float> outputMap = new HashMap<>();
        final Map<LineKey, Float> inputMap = new HashMap<>();
        final Map<LineKey, Float> propertyMap = new HashMap<>();

        for (final Node node : graph.getNodes()
            .values()) {
            final var nb = balance.nodeBalances()
                .get(node.getId());
            if (nb == null) continue;

            final float scale = opsMode ? 1f : (float) cycleTicks / Math.max(1, nb.durationPerOp());

            for (int i = 0; i < node.getOutputs()
                .size(); i++) {
                final Float total = nb.effectiveOutputs()
                    .get(i);
                if (total == null || total <= 0) continue;
                outputMap.merge(
                    LineKey.ResourceKey.of(
                        node.getOutputs()
                            .get(i)),
                    total * scale,
                    Float::sum);
            }

            for (int i = 0; i < node.getInputs()
                .size(); i++) {
                final Float total = nb.effectiveInputs()
                    .get(i);
                if (total == null || total <= 0) continue;
                inputMap.merge(
                    LineKey.ResourceKey.of(
                        node.getInputs()
                            .get(i)),
                    total * scale,
                    Float::sum);
            }
        }

        // Net by resource: output = max(0, prod - cons), input = max(0, cons - prod).
        // The tolerance has to clear the accumulated float error, not one float's worth of it:
        // these are sums over every port carrying the resource, so error grows with the number of
        // contributors, and at 1e-6 a fully recycled ingredient on a large chart prints a ghost
        // line for a rate that is really zero.
        final Map<LineKey, Float> netInputs = new HashMap<>();
        for (final var entry : inputMap.entrySet()) {
            final LineKey key = entry.getKey();
            final float cons = entry.getValue();
            final float prod = outputMap.getOrDefault(key, 0f);
            final float eps = Math.max(prod, cons) * NET_EPS;
            if (Math.abs(cons - prod) <= eps) {
                outputMap.remove(key);
            } else if (cons > prod) {
                netInputs.put(key, cons - prod);
                outputMap.remove(key);
            } else {
                outputMap.put(key, prod - cons);
            }
        }
        inputMap.clear();
        inputMap.putAll(netInputs);

        for (final var entry : balance.propertyTotals()
            .entrySet()) {
            if (!(entry.getKey() instanceof SummaryProperty<?>)) continue;
            propertyMap.merge(
                new LineKey.PropertyKey((SummaryProperty<?>) entry.getKey()),
                (float) entry.getValue(),
                Float::sum);
        }

        // Opposite ways on purpose: an output list leads with the headline product, an input list
        // with the scarcest ingredient. Name breaks ties, or equal flows shuffle between frames.
        return new Summary(
            flatten(outputMap, BY_AMOUNT.reversed()),
            flatten(inputMap, BY_AMOUNT),
            flatten(propertyMap, BY_AMOUNT));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Line<?>> flatten(final Map<LineKey, Float> map, final Comparator<Line<?>> order) {
        final List<Line<?>> result = new ArrayList<>();
        for (final var entry : map.entrySet()) {
            if (entry.getValue() <= 0) continue;
            final Line<?> line = switch (entry.getKey()) {
                case LineKey.ResourceKey rk -> rk.toLine(entry.getValue());
                case LineKey.PropertyKey pk   -> new Line(pk.prop(), pk.prop().getDefaultValue(), entry.getValue());
            };
            result.add(line);
        }
        result.sort(order);
        return result;
    }
}
