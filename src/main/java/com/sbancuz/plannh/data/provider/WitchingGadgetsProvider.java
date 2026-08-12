package com.sbancuz.plannh.data.provider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.effect.Effects;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.setting.Settings;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import witchinggadgets.client.nei.NEIInfernalBlastfurnaceHandler;
import witchinggadgets.client.nei.NEISpinningWheelHandler;
import witchinggadgets.common.util.recipe.InfernalBlastfurnaceRecipe;

public final class WitchingGadgetsProvider implements PropertyProvider {

    // TODO: Make a PR to expose this constant
    // Spinning Wheel: base process length (ticks) from TileEntitySpinningWheel
    private static final int SPINNING_WHEEL_TICKS = 120;

    @Override
    public void register() {
        RecipePropertyAPI.registerExtractor(NEISpinningWheelHandler.class, this);
        RecipePropertyAPI.registerExtractor(NEIInfernalBlastfurnaceHandler.class, this);

        MachineProfileRegistry.register(
            MachineProfile.builder("wg", "Witching Gadgets")
                .setting(Settings.MACHINES.def())
                .setting(Settings.TICK_MODIFIER.def())
                .effect(
                    Effects.clearCost()
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
            case NEISpinningWheelHandler _, NEIInfernalBlastfurnaceHandler _ -> "wg";
            default -> null;
        };
    }

    @Override
    @Nonnull
    public Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final Map<RecipeProperty<?>, Object> props = new HashMap<>(
            PropertyProvider.super.extract(node, handler, recipeIndex));

        if (handler instanceof NEISpinningWheelHandler) {
            props.put(RecipePropertyAPI.DURATION_TICKS, SPINNING_WHEEL_TICKS);
        } else if (handler instanceof NEIInfernalBlastfurnaceHandler) {
            final List<PositionedStack> inputs = handler.getIngredientStacks(recipeIndex);
            for (final PositionedStack ps : inputs) {
                if (ps != null && ps.item != null) {
                    final InfernalBlastfurnaceRecipe recipe = InfernalBlastfurnaceRecipe.getRecipeForInput(ps.item);
                    if (recipe != null) {
                        props.put(RecipePropertyAPI.DURATION_TICKS, recipe.getSmeltingTime());
                        break;
                    }
                }
            }
        }

        return props;
    }
}
