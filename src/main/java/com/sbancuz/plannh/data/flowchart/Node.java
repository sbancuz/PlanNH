package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.fluids.FluidStack;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.ExtractedProperties;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.RecipeProperty;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;
import it.unimi.dsi.fastutil.objects.ObjectFloatImmutablePair;

public class Node {

    public final UUID id;
    public int x;
    public int y;

    /// These fields also store their resource consumption percentage
    public final List<ObjectFloatImmutablePair<ItemStack>> inputs;
    public final List<ObjectFloatImmutablePair<ItemStack>> outputs;
    public final List<ObjectFloatImmutablePair<FluidStack>> fluidInputs = new ArrayList<>();
    public final List<ObjectFloatImmutablePair<FluidStack>> fluidOutputs = new ArrayList<>();

    public String machineName;
    public int durationTicks;
    public Recipe.RecipeId recipeId;
    public int handlerRecipeIndex;
    public final MachineConfig machineConfig = new MachineConfig();

    public final ExtractedProperties properties = new ExtractedProperties();

    public Node(final IRecipeHandler handler, final int recipeIndex, final int x, final int y) {
        this.id = UUID.randomUUID();
        this.x = x;
        this.y = y;

        this.machineName = handler.getRecipeName()
            .trim();
        this.recipeId = Recipe.RecipeId.of(handler, recipeIndex);
        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();

        final List<PositionedStack> ins = handler.getIngredientStacks(recipeIndex);
        for (final PositionedStack ps : ins) {
            if (ps != null && ps.item != null && ps.item.stackSize > 0) {
                this.inputs.add(new ObjectFloatImmutablePair<>(ps.item.copy(), 1.f));
            }
        }

        final PositionedStack result = handler.getResultStack(recipeIndex);
        if (result != null && result.item != null) {
            this.outputs.add(new ObjectFloatImmutablePair<>(result.item.copy(), 1.f));
        }
        final List<PositionedStack> others = handler.getOtherStacks(recipeIndex);
        for (final PositionedStack ps : others) {
            if (ps != null && ps.item != null && TileEntityFurnace.getItemBurnTime(ps.item) <= 0) {
                this.outputs.add(new ObjectFloatImmutablePair<>(ps.item.copy(), 1.f));
            }
        }

        for (final PropertyProvider ex : RecipePropertyAPI.getExtractors()) {
            try {
                final Map<RecipeProperty<?>, Object> props = ex.extract(this, handler, recipeIndex);
                if (props != null && !props.isEmpty()) {
                    this.properties.putAll(props);
                }
            } catch (final Exception ignored) {}
        }

        for (final PropertyProvider ex : RecipePropertyAPI.getExtractors()) {
            try {
                final String pid = ex.getProfileId(handler, recipeIndex);
                if (pid != null && !MachineProfileRegistry.defaultId()
                    .equals(pid)) {
                    this.machineConfig.profileId = pid;
                    break;
                }
            } catch (final Exception ignored) {}
        }
        this.machineConfig.initDefaults();

        this.durationTicks = this.properties.get(RecipePropertyAPI.DURATION_TICKS);
    }

    /**
     * To be used only for serialization/deserialization
     */
    public Node(final UUID id, final int x, final int y) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();
    }
}
