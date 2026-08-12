package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.bdew.gendustry.machines.extractor.MachineExtractor;
import net.bdew.gendustry.machines.imprinter.MachineImprinter;
import net.bdew.gendustry.machines.liquifier.MachineLiquifier;
import net.bdew.gendustry.machines.mproducer.MachineMutagenProducer;
import net.bdew.gendustry.machines.mutatron.MachineMutatron;
import net.bdew.gendustry.machines.replicator.MachineReplicator;
import net.bdew.gendustry.machines.sampler.MachineSampler;
import net.bdew.gendustry.machines.transposer.MachineTransposer;
import net.bdew.gendustry.nei.ExtractorHandler;
import net.bdew.gendustry.nei.ImprinterHandler;
import net.bdew.gendustry.nei.LiquifierHandler;
import net.bdew.gendustry.nei.MutagenProducerHandler;
import net.bdew.gendustry.nei.MutatronHandler;
import net.bdew.gendustry.nei.ReplicatorHandler;
import net.bdew.gendustry.nei.SamplerHandler;
import net.bdew.gendustry.nei.TransposerHandler;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.effect.steps.CoFHCompat;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.setting.Settings;

import codechicken.nei.recipe.IRecipeHandler;

public final class GendustryProvider implements PropertyProvider {

    private static final String PROFILE_ID = "gendustry:basic";

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(MutatronHandler.class, this);
        RecipePropertyAPI.registerExtractor(MutagenProducerHandler.class, this);
        RecipePropertyAPI.registerExtractor(SamplerHandler.class, this);
        RecipePropertyAPI.registerExtractor(ImprinterHandler.class, this);
        RecipePropertyAPI.registerExtractor(ExtractorHandler.class, this);
        RecipePropertyAPI.registerExtractor(LiquifierHandler.class, this);
        RecipePropertyAPI.registerExtractor(ReplicatorHandler.class, this);
        RecipePropertyAPI.registerExtractor(TransposerHandler.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder(PROFILE_ID, "Gendustry")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(
                    Effects.durationFromHandler()
                        .amortizeCost(CoFHCompat.RF_COST)
                        .applyParallelism())
                .build());
    }

    @Override
    public boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        return getProfileId(handler, recipeIndex) != null;
    }

    @Override
    @Nullable
    public String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return switch (handler) {
            case MutatronHandler _, MutagenProducerHandler _, SamplerHandler _,
                 ImprinterHandler _, ExtractorHandler _, LiquifierHandler _,
                 ReplicatorHandler _, TransposerHandler _ -> PROFILE_ID;
            default -> null;
        };
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>(
            PropertyProvider.super.extract(node, handler, recipeIndex));

        final float mj;
        final float power;

        switch (handler) {
            case MutatronHandler _ -> {
                mj = MachineMutatron.mjPerItem();
                power = MachineMutatron.powerUseRate();
            }
            case MutagenProducerHandler _ -> {
                mj = MachineMutagenProducer.mjPerItem();
                power = MachineMutagenProducer.powerUseRate();
            }
            case SamplerHandler _ -> {
                mj = MachineSampler.mjPerItem();
                power = MachineSampler.powerUseRate();
            }
            case ImprinterHandler _ -> {
                mj = MachineImprinter.mjPerItem();
                power = MachineImprinter.powerUseRate();
            }
            case ExtractorHandler _ -> {
                mj = MachineExtractor.mjPerItem();
                power = MachineExtractor.powerUseRate();
            }
            case LiquifierHandler _ -> {
                mj = MachineLiquifier.mjPerItem();
                power = MachineLiquifier.powerUseRate();
            }
            case ReplicatorHandler _ -> {
                mj = MachineReplicator.mjPerItem();
                power = MachineReplicator.powerUseRate();
            }
            case TransposerHandler _ -> {
                mj = MachineTransposer.mjPerItem();
                power = MachineTransposer.powerUseRate();
            }
            default -> {
                return props;
            }
        }

        final int ticks = (int) (mj / power);
        props.put(RecipePropertyAPI.DURATION_TICKS, Math.max(1, ticks));
        props.put(CoFHCompat.RF_COST, (long) mj);

        return props;
    }
}
