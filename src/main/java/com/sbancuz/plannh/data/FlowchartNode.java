package com.sbancuz.plannh.data;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.sbancuz.plannh.api.RecipePropertyAPI;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;

public class FlowchartNode {

    public final UUID id;
    public int x;
    public int y;

    public final List<ItemStack> inputs;
    public final List<ItemStack> outputs;
    public final List<FluidStack> fluidInputs = new ArrayList<>();
    public final List<FluidStack> fluidOutputs = new ArrayList<>();

    public String machineName;
    public int durationTicks;
    public String recipeOwner;
    public int handlerRecipeIndex;
    public final MachineConfig machineConfig = new MachineConfig();

    public final ExtractedProperties properties = new ExtractedProperties();

    private static Field cachedGtRecipeField;
    private static Field cachedFluidInputsField;
    private static Field cachedFluidOutputsField;
    private static boolean cachedFluidFieldsAttempted = false;

    public FlowchartNode(IRecipeHandler handler, int recipeIndex, int x, int y) {
        this.id = UUID.randomUUID();
        this.x = x;
        this.y = y;

        this.machineName = handler.getRecipeName()
            .trim();
        String ident = handler.getOverlayIdentifier();
        if (ident != null && !ident.isEmpty()) {
            this.recipeOwner = ident;
            this.handlerRecipeIndex = recipeIndex;
        }

        this.inputs = new ArrayList<>();
        this.outputs = new ArrayList<>();

        List<PositionedStack> ins = handler.getIngredientStacks(recipeIndex);
        for (PositionedStack ps : ins) {
            if (ps != null && ps.item != null && ps.item.stackSize > 0) {
                if (!isFluidContainer(ps.item)) {
                    this.inputs.add(ps.item.copy());
                }
            }
        }

        PositionedStack result = handler.getResultStack(recipeIndex);
        if (result != null && result.item != null && !isFluidContainer(result.item)) {
            this.outputs.add(result.item.copy());
        }
        List<PositionedStack> others = handler.getOtherStacks(recipeIndex);
        for (PositionedStack ps : others) {
            if (ps != null && ps.item != null && !isFluidContainer(ps.item)) {
                this.outputs.add(ps.item.copy());
            }
        }

        for (RecipePropertyExtractor ex : RecipePropertyAPI.getExtractors()) {
            if (ex.canHandle(this.recipeOwner)) {
                this.properties.putAll(ex.extract(handler, recipeIndex));
            }
        }

        this.durationTicks = this.properties.get(RecipePropertyAPI.DURATION_TICKS);

        extractFluidsFromGT(handler, recipeIndex);
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

    private static boolean isFluidContainer(ItemStack stack) {
        if (FluidContainerRegistry.getFluidForFilledItem(stack) != null) return true;
        if (stack.getItem() instanceof IFluidContainerItem container) {
            FluidStack held = container.getFluid(stack);
            return held != null && held.amount > 0;
        }
        return false;
    }

    private void extractFluidsFromGT(IRecipeHandler handler, int recipeIndex) {
        try {
            Class<?> gthClass = Class.forName("gregtech.nei.GTNEIDefaultHandler");
            if (!gthClass.isInstance(handler)) return;

            List<?> recipes = RecipeHandlerAccess.getArecipes((codechicken.nei.recipe.TemplateRecipeHandler) handler);
            if (recipeIndex < 0 || recipeIndex >= recipes.size()) return;

            Object cached = recipes.get(recipeIndex);

            if (cachedGtRecipeField == null) {
                cachedGtRecipeField = cached.getClass()
                    .getField("mRecipe");
            }
            Object r = cachedGtRecipeField.get(cached);
            if (r == null) return;

            if (!cachedFluidFieldsAttempted) {
                try {
                    cachedFluidInputsField = r.getClass()
                        .getField("mFluidInputs");
                    cachedFluidOutputsField = r.getClass()
                        .getField("mFluidOutputs");
                } catch (Exception ignored) {}
                cachedFluidFieldsAttempted = true;
            }

            if (cachedFluidInputsField != null) {
                FluidStack[] fin = (FluidStack[]) cachedFluidInputsField.get(r);
                if (fin != null) for (FluidStack fs : fin) fluidInputs.add(fs.copy());
            }
            if (cachedFluidOutputsField != null) {
                FluidStack[] fout = (FluidStack[]) cachedFluidOutputsField.get(r);
                if (fout != null) for (FluidStack fs : fout) fluidOutputs.add(fs.copy());
            }
        } catch (Exception ignored) {}
    }
}
