package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.annotation.Versioned;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.effect.steps.CoFHCompat;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.setting.Settings;

import appeng.api.AEApi;
import appeng.api.features.IGrinderEntry;
import appeng.integration.modules.NEIHelpers.NEIAEShapedRecipeHandler;
import appeng.integration.modules.NEIHelpers.NEIAEShapelessRecipeHandler;
import appeng.integration.modules.NEIHelpers.NEIGrinderRecipeHandler;
import appeng.integration.modules.NEIHelpers.NEIInscriberRecipeHandler;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;

@Versioned.Mod(modId = Compat.IDs.AE2, sinceVersion = "rv3-beta-1017-GTNH")
public final class AE2Provider implements PropertyProvider {

    @Versioned.Class
    public static class TileInscriber {

        public static final int MAX_PROCESSING_TIME = 100;
        public static final int BASE_POWER_PER_TICK = 10;
        public static final int BASE_SPEED = 1;
    }

    @Versioned.Class
    public static class TileMolecularAssembler {

        public static final int MAX_PROCESSING_TIME = 100;
        public static final int[] SPEED = { 10, 13, 17, 20, 25, 50 };
        public static final double[] ACCELERATION_TAX = { 1.0, 1.3, 1.7, 2.0, 2.5, 5.0 };
    }

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(NEIGrinderRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(NEIInscriberRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(NEIAEShapedRecipeHandler.class, this);
        RecipePropertyAPI.registerExtractor(NEIAEShapelessRecipeHandler.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder("ae2:quartz_grinder", "Quartz Grinder")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .setting(Settings.DURATION_TICKS.def())
                .effect(DefaultProvider::noopEffect)
                .build());

        MachineProfileRegistry.register(
            MachineProfile.builder("ae2:inscriber", "Inscriber")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .setting(Settings.CATALYST_ACCEL_CARD.def())
                .effect(Effects.durationFromFormula((ctx, s) -> {
                    final int cards = (int) s.getOrDefault(Settings.CATALYST_ACCEL_CARD.key(), 0);
                    final int speedFactor = TileInscriber.BASE_SPEED + cards;
                    return Math.max(1, (TileInscriber.MAX_PROCESSING_TIME + speedFactor - 1) / speedFactor);
                })
                    .withCostPerT(CoFHCompat.RF_PER_T, (current, s, ctx) -> {
                        final int cards = (int) s.getOrDefault(Settings.CATALYST_ACCEL_CARD.key(), 0);
                        final int speedFactor = TileInscriber.BASE_SPEED + cards;
                        return (long) TileInscriber.BASE_POWER_PER_TICK * speedFactor;
                    })
                    .applyParallelism()
                    .computeTotal(CoFHCompat.RF_COST))
                .build());

        MachineProfileRegistry.register(
            MachineProfile.builder("ae2:molecular_assembler", "Molecular Assembler")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .setting(Settings.CATALYST_ACCEL_CARD.def())
                .effect(Effects.durationFromFormula((ctx, s) -> {
                    final int cards = Math.min(
                        (int) s.getOrDefault(Settings.CATALYST_ACCEL_CARD.key(), 0),
                        TileMolecularAssembler.SPEED.length - 1);
                    final int speed = TileMolecularAssembler.SPEED[cards];
                    return Math.max(1, (TileMolecularAssembler.MAX_PROCESSING_TIME + speed - 1) / speed);
                })
                    .andThen((current, s, ctx) -> {
                        final int cards = Math.min(
                            (int) s.getOrDefault(Settings.CATALYST_ACCEL_CARD.key(), 0),
                            TileMolecularAssembler.SPEED.length - 1);
                        final int speed = TileMolecularAssembler.SPEED[cards];
                        final double tax = TileMolecularAssembler.ACCELERATION_TAX[cards];
                        current.energyPerT(Math.round(speed * tax));
                        return current;
                    })
                    .applyParallelism())
                .build());
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return switch (handler) {
            case NEIGrinderRecipeHandler _ -> "ae2:quartz_grinder";
            case NEIInscriberRecipeHandler _-> "ae2:inscriber";
            case NEIAEShapedRecipeHandler _, NEIAEShapelessRecipeHandler _ -> "ae2:molecular_assembler";
            default -> null;
        };
    }

    @Override
    public boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        return handler instanceof NEIGrinderRecipeHandler || handler instanceof NEIInscriberRecipeHandler
            || handler instanceof NEIAEShapedRecipeHandler
            || handler instanceof NEIAEShapelessRecipeHandler;
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>(
            PropertyProvider.super.extract(node, handler, recipeIndex));

        if (handler instanceof NEIGrinderRecipeHandler) {
            final int duration = findGrinderDuration(handler, recipeIndex);
            if (duration > 0) {
                props.put(RecipePropertyAPI.DURATION_TICKS, duration);
            }
        }

        return props;
    }

    private static int findGrinderDuration(final IRecipeHandler handler, final int recipeIndex) {
        final PositionedStack result = handler.getResultStack(recipeIndex);
        if (result == null || result.item == null) return 0;

        for (final IGrinderEntry entry : AEApi.instance()
            .registries()
            .grinder()
            .getRecipes()) {
            if (NEIServerUtils.areStacksSameTypeCrafting(entry.getOutput(), result.item)) {
                return entry.getEnergyCost();
            }
        }
        return 0;
    }
}
