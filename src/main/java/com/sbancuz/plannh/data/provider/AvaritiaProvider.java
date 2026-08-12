package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.properties.SummaryProperty;
import com.sbancuz.plannh.data.setting.Settings;

import codechicken.nei.recipe.IRecipeHandler;
import fox.spiteful.avaritia.compat.nei.CompressionHandler;

public final class AvaritiaProvider implements PropertyProvider {

    public static final RecipeProperty<Integer> COMPRESSION_COST = SummaryProperty
        .<Integer>builder("avaritia.compression_cost", 0)
        .build();

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(CompressionHandler.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder("avaritia:neutronium_compressor", "Neutronium Compressor")
                .setting(Settings.MACHINES.def())
                .setting(Settings.INPUTS_PER_TICK.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(
                    Effects.durationFromTotal(COMPRESSION_COST, Settings.INPUTS_PER_TICK.key(), 1)
                        .amortizeCost(COMPRESSION_COST)
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
            case CompressionHandler _ -> "avaritia:neutronium_compressor";
            default -> null;
        };
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>(
            PropertyProvider.super.extract(node, handler, recipeIndex));

        if (handler instanceof CompressionHandler && !node.getInputs()
            .isEmpty()) {
            final ItemStack stack = (ItemStack) node.getInputs()
                .getFirst()
                .getValue();
            if (stack != null && stack.stackSize > 0) {
                props.put(COMPRESSION_COST, stack.stackSize);
            }
        }

        return props;
    }
}
