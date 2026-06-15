package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceResult;

public record Summary(List<Line> outputs, List<Line> inputs, List<Line> properties) {

    public enum SummaryMode {
        CYCLES,
        THROUGHPUT
    }

    public static class Line {

        public final String type;
        public final Object resource;
        public float count;

        public Line(final String type, final Object resource, final float count) {
            this.type = type;
            this.resource = resource;
            this.count = count;
        }
    }

    private record LineKey(String type, Object resource) {

        LineKey(final Port port) {
            this(
                port.getType()
                    .getKey(),
                port.getValue());
        }

        LineKey(final RecipeProperty<?> prop) {
            this(prop.getKey(), prop);
        }

        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof LineKey(final String type1, final Object resource1))) return false;
            if (!type.equals(type1)) return false;
            return switch (type) {
                case "item" -> ((ItemStack) resource).isItemEqual((ItemStack) resource1);
                case "fluid" -> ((FluidStack) resource).isFluidEqual((FluidStack) resource1);
                default -> resource.equals(resource1);
            };
        }

        @Override
        public int hashCode() {
            return switch (type) {
                case "item" -> {
                    final ItemStack s = (ItemStack) resource;
                    yield 31 * s.getItem()
                        .hashCode() + s.getItemDamage();
                }
                case "fluid" -> ((FluidStack) resource).getFluid()
                    .hashCode();
                default -> resource.hashCode();
            };
        }
    }

    public static Summary compute(final BalanceResult balance, final Graph graph) {
        final Map<LineKey, Float> outputMap = new HashMap<>();
        final Map<LineKey, Float> inputMap = new HashMap<>();
        final Map<LineKey, Float> propertyMap = new HashMap<>();

        for (final Node node : graph.getNodes()) {
            final var nb = balance.nodeBalances()
                .get(node.id);
            if (nb == null) continue;

            for (int i = 0; i < node.outputs.size(); i++) {
                final Float total = nb.effectiveOutputs.get(i);
                if (total == null || total <= 0) continue;
                outputMap.merge(new LineKey(node.outputs.get(i)), total, Float::sum);
            }

            for (int i = 0; i < node.inputs.size(); i++) {
                final Float total = nb.effectiveInputs.get(i);
                if (total == null || total <= 0) continue;
                final LineKey key = new LineKey(node.inputs.get(i));
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
            propertyMap.merge(new LineKey(entry.getKey()), (float) entry.getValue(), Float::sum);
        }

        return new Summary(flatten(outputMap), flatten(inputMap), flatten(propertyMap));
    }

    private static List<Line> flatten(final Map<LineKey, Float> map) {
        final List<Line> result = new ArrayList<>();
        for (final var entry : map.entrySet()) {
            if (entry.getValue() <= 0) continue;
            result.add(new Line(entry.getKey().type, entry.getKey().resource, entry.getValue()));
        }
        return result;
    }
}
