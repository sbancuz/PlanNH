package com.sbancuz.plannh.data.effect;

import java.util.HashMap;
import java.util.Map;

import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.setting.Settings;

@FunctionalInterface
public interface EffectComputer {

    EffectResult compute(Map<String, Object> settings, RecipeContext ctx);

    default EffectComputer andThen(final EffectStep step) {
        final EffectComputer first = this;
        return new EffectComputer() {
            @Override
            public EffectResult compute(final Map<String, Object> settings, final RecipeContext ctx) {
                return step.apply(first.compute(settings, ctx), settings, ctx);
            }

            @Override
            public Map<String, Object> routeDefaults(final RecipeContext ctx) {
                final Map<String, Object> merged = new HashMap<>(first.routeDefaults(ctx));
                if (step instanceof final EffectComputer ec) {
                    merged.putAll(ec.routeDefaults(ctx));
                }
                return merged;
            }
        };
    }

    /**
     * Per-recipe-map default overrides contributed by this effect computer, keyed by setting key.
     * Empty by default; only effect steps that define route defaults (e.g. per-recipe-machine
     * Perfect OC) override this. Used to seed node settings so route defaults show immediately.
     */
    default Map<String, Object> routeDefaults(final RecipeContext ctx) {
        return Map.of();
    }

    default EffectComputer withTotalCost(final RecipeProperty<? extends Number> costProperty,
        final EffectFunction<Long> costFn) {
        return (s, ctx) -> {
            final EffectResult res = this.compute(s, ctx);
            final long cost = costFn.apply(res, s, ctx);
            res.energyPerT(cost / res.durationTicks());
            ctx.properties()
                .putIfAbsent(costProperty, cost);
            return res;
        };
    }

    default EffectComputer withAmortizedCost(final RecipeProperty<? extends Number> costProperty,
        final EffectFunction<Long> costFn) {
        return (s, ctx) -> {
            final EffectResult res = this.compute(s, ctx);
            final long cost = costFn.apply(res, s, ctx);
            res.energyPerT(cost / res.durationTicks());
            ctx.properties()
                .putIfAbsent(costProperty, cost / res.durationTicks());
            return res;
        };
    }

    default EffectComputer withCostPerT(final RecipeProperty<? extends Number> costProperty,
        final EffectFunction<Long> costFn) {
        return (s, ctx) -> {
            final EffectResult res = this.compute(s, ctx);
            final long cost = costFn.apply(res, s, ctx);
            res.energyPerT(cost);
            ctx.properties()
                .putIfAbsent(costProperty, cost);
            return res;
        };
    }

    default EffectComputer computeTotal(final RecipeProperty<? extends Number> costProperty) {
        return (s, ctx) -> {
            final EffectResult res = this.compute(s, ctx);
            ctx.properties()
                .putIfAbsent(costProperty, res.energyPerT() * res.durationTicks() * res.throughputFactor());
            return res;
        };
    }

    default EffectComputer amortizeCost(final RecipeProperty<? extends Number> prop) {
        return (s, ctx) -> {
            final EffectResult res = this.compute(s, ctx);
            if (res.energyPerT() == 0 && res.durationTicks() > 0) {
                final Number total = ctx.getOrDefault(prop, null);
                if (total != null && total.longValue() > 0) {
                    res.energyPerT(total.longValue() / res.durationTicks());
                }
            }
            return res;
        };
    }

    default EffectComputer applyParallelism() {
        return (s, ctx) -> {
            final EffectResult res = this.compute(s, ctx);
            final int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
            final int parallels = MachineProfile.getInt(s, Settings.PARALLELS.key(), 1);
            res.throughputFactor(res.throughputFactor() * machines * parallels);
            return res;
        };
    }
}
