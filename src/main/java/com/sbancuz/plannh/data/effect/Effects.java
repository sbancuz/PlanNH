package com.sbancuz.plannh.data.effect;

import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.properties.RecipeProperty;

public final class Effects {

    private Effects() {}

    public static EffectComputer constant(final int durationTicks, final long energyPerTick) {
        return (s, ctx) -> new EffectResult(durationTicks, energyPerTick, 1);
    }

    public static EffectComputer durationFromHandler() {
        return (s, ctx) -> {
            final int duration = (int) (Integer) ctx.getOrDefault(RecipePropertyAPI.DURATION_TICKS, 0);
            return new EffectResult(duration, 0, 1);
        };
    }

    public static EffectComputer durationFromTotal(final RecipeProperty<? extends Number> prop,
        final String rateKey, final int defaultRate) {
        return (s, ctx) -> {
            final Object dur = ctx.properties().get(RecipePropertyAPI.DURATION_TICKS);
            int duration = dur instanceof final Number n ? n.intValue() : 0;
            if (duration <= 0) {
                final int rate = MachineProfile.getInt(s, rateKey, defaultRate);
                final Number total = ctx.getOrDefault(prop, null);
                if (rate > 0 && total != null && total.longValue() > 0) {
                    duration = Math.max(1, (int) (total.longValue() / rate));
                }
            }
            return new EffectResult(duration, 0, 1);
        };
    }

    public static EffectComputer durationFromFormula(final ToIntBiFunction<RecipeContext, Map<String, Object>> formula) {
        return (s, ctx) -> {
            final Object dur = ctx.properties().get(RecipePropertyAPI.DURATION_TICKS);
            int duration = dur instanceof final Number n ? n.intValue() : 0;
            if (duration <= 0) {
                duration = Math.max(1, formula.applyAsInt(ctx, s));
            }
            return new EffectResult(duration, 0, 1);
        };
    }

    public static EffectComputer onlyIf(final Predicate<Map<String, Object>> condition,
        final EffectComputer delegate) {
        return (s, ctx) -> {
            if (condition.test(s)) return delegate.compute(s, ctx);
            final Object dur = ctx.properties().get(RecipePropertyAPI.DURATION_TICKS);
            return new EffectResult(dur instanceof final Number n ? n.intValue() : 0, 0, 1);
        };
    }

    public static EffectComputer clearCost() {
        return (s, ctx) -> {
            final Object dur = ctx.properties().get(RecipePropertyAPI.DURATION_TICKS);
            final int d = dur instanceof final Number n ? n.intValue() : 0;
            return new EffectResult(d, 0, 1);
        };
    }

    public static EffectComputer firstOf(final EffectComputer... alternatives) {
        return (s, ctx) -> {
            for (final var alt : alternatives) {
                final var result = alt.compute(s, ctx);
                if (result.durationTicks() > 0) return result;
            }
            return alternatives[0].compute(s, ctx);
        };
    }
}
