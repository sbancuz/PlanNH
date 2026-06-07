package com.sbancuz.plannh.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import codechicken.nei.recipe.Recipe;
import gregtech.common.items.ItemFluidDisplay;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.sbancuz.plannh.api.RecipePropertyAPI;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import it.unimi.dsi.fastutil.objects.ObjectFloatImmutablePair;

public class FlowchartNode {

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

    public FlowchartNode(IRecipeHandler handler, int recipeIndex, int x, int y) {
        this.id = UUID.randomUUID();
        this.x = x;
        this.y = y;

        this.machineName = handler.getRecipeName()
            .trim();
        this.recipeId = Recipe.RecipeId.of(handler, recipeIndex);
        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();

        List<PositionedStack> ins = handler.getIngredientStacks(recipeIndex);
        for (PositionedStack ps : ins) {
            if (ps != null && ps.item != null && ps.item.stackSize > 0) {
                this.inputs.add(new ObjectFloatImmutablePair<>(ps.item.copy(), 1.f));
            }
        }

        PositionedStack result = handler.getResultStack(recipeIndex);
        if (result != null && result.item != null) {
            this.outputs.add(new ObjectFloatImmutablePair<>(result.item.copy(), 1.f));
        }
        List<PositionedStack> others = handler.getOtherStacks(recipeIndex);
        for (PositionedStack ps : others) {
            if (ps != null && ps.item != null) {
                this.outputs.add(new ObjectFloatImmutablePair<>(ps.item.copy(), 1.f));
            }
        }

        for (RecipePropertyExtractor ex : RecipePropertyAPI.getExtractors()) {
            if (ex.canHandle(handler.getOverlayIdentifier())) {
                this.properties.putAll(ex.extract(this, handler, recipeIndex));
            }
        }

        this.durationTicks = this.properties.get(RecipePropertyAPI.DURATION_TICKS);
    }

    /**
     * To be used only for serialization/deserialization
     */
    public FlowchartNode(UUID id, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();
    }
}
