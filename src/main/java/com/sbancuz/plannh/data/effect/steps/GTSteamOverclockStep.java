package com.sbancuz.plannh.data.effect.steps;

import static com.sbancuz.plannh.data.provider.GTProvider.EU_PER_TICK;
import static com.sbancuz.plannh.data.provider.GTProvider.TOTAL_EU;
import static com.sbancuz.plannh.data.provider.GTSteamProvider.STEAM_EU_PERT;
import static com.sbancuz.plannh.data.provider.GTSteamProvider.TOTAL_STEAM_EU;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.effect.EffectComputer;
import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.effect.EffectStep;
import com.sbancuz.plannh.data.setting.Settings;

public class GTSteamOverclockStep implements EffectStep, EffectComputer {

    private GTSteamOverclockStep() {}

    public static GTSteamOverclockStep create() {
        return new GTSteamOverclockStep();
    }

    @Override
    public EffectResult compute(final java.util.Map<String, Object> s, final RecipeContext ctx) {
        final Object dur = ctx.properties().get(RecipePropertyAPI.DURATION_TICKS);
        final int d = dur instanceof final Number n ? n.intValue() : 0;
        return apply(new EffectResult(d, 0, 1), s, ctx);
    }

    @Override
    public EffectResult apply(final EffectResult current, final java.util.Map<String, Object> s,
        final RecipeContext ctx) {
        final long recipeEUt = recipeEUt(ctx);
        final int recipeDuration = current.durationTicks();

        if (recipeEUt <= 0 || recipeDuration <= 0) {
            current.durationTicks(recipeDuration);
            current.energyPerT(recipeEUt);
            return current;
        }

        final int eutDiscount = MachineProfile.getInt(s, Settings.STEAM_EUT_DISCOUNT.key(), 100);
        final int durationModifier = MachineProfile.getInt(s, Settings.STEAM_DURATION_MODIFIER.key(), 100);
        final int parallels = MachineProfile.getInt(s, Settings.PARALLELS.key(), 1);
        final int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);

        final long steamPerTick = recipeEUt * eutDiscount / 100;
        final int duration = Math.max(1, recipeDuration * durationModifier / 100);

        current.energyPerT(steamPerTick);
        current.durationTicks(duration);
        current.throughputFactor(parallels * machines);

        ctx.properties()
            .putIfAbsent(STEAM_EU_PERT, steamPerTick);
        ctx.properties()
            .putIfAbsent(TOTAL_STEAM_EU, steamPerTick * duration);

        // Clear raw EU values so they don't appear in summary
        ctx.properties()
            .put(EU_PER_TICK, 0L);
        ctx.properties()
            .put(TOTAL_EU, 0L);

        return current;
    }

    static long recipeEUt(final RecipeContext ctx) {
        final Long euPerTick = ctx.getOrDefault(EU_PER_TICK, null);
        if (euPerTick != null && euPerTick > 0) return euPerTick;
        final Long totalEu = ctx.getOrDefault(TOTAL_EU, null);
        if (totalEu != null && totalEu > 0) {
            final Object dur = ctx.properties().get(RecipePropertyAPI.DURATION_TICKS);
            final int d = dur instanceof final Number n ? n.intValue() : 0;
            if (d > 0) return totalEu / d;
        }
        return 0;
    }
}
