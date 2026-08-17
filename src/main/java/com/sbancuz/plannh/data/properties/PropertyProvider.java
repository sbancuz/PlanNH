package com.sbancuz.plannh.data.properties;

import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntityFurnace;

import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.setting.Settings;
import com.sbancuz.plannh.nei.NEIPlanConfig;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;

public interface PropertyProvider {

    void register();

    default Map<RecipeProperty<?>, Object> extract(final Node node, final IRecipeHandler handler,
        final int recipeIndex) {
        final List<PositionedStack> ins = handler.getIngredientStacks(recipeIndex);
        for (final PositionedStack ps : ins) if (ps != null && ps.item != null) node.getInputs()
            .add(Port.itemPort(ps));

        final PositionedStack result = handler.getResultStack(recipeIndex);
        if (result != null && result.item != null) node.getOutputs()
            .add(Port.itemPort(result));

        final List<PositionedStack> others = handler.getOtherStacks(recipeIndex);
        for (final PositionedStack ps : others) {
            if (ps != null && ps.item != null) {
                if (NEIClientConfig.getSetting(NEIPlanConfig.ConfigBurnableOverride.KEY)
                    .getIntValue(NEIPlanConfig.ConfigBurnableOverride.OFF) == NEIPlanConfig.ConfigBurnableOverride.ON) {
                    if (node.getMachineConfig()
                        .getString(Settings.BURNABLE_OVERRIDE.key())
                        .equals("IN")) {
                        node.getInputs()
                            .add(Port.itemPort(ps));
                    } else if (node.getMachineConfig()
                        .getString(Settings.BURNABLE_OVERRIDE.key())
                        .equals("OUT")) {
                            node.getOutputs()
                                .add(Port.itemPort(ps));
                        }
                } else if (TileEntityFurnace.getItemBurnTime(ps.item) <= 0) {
                    node.getOutputs()
                        .add(Port.itemPort(ps));
                }
            }
        }

        return Map.of();
    }

    @Nullable
    default String getProfileId(final IRecipeHandler handler, final int recipeIndex) {
        return null;
    }

    default boolean canCraft(final IRecipeHandler handler, final int recipeIndex) {
        return true;
    }

    @Nonnull
    default String getExtractorName() {
        return getClass().getSimpleName();
    }
}
