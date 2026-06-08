package com.sbancuz.plannh.data.flowchart;

import java.util.List;
import java.util.Map;

import com.sbancuz.plannh.data.RecipeProperty;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public record Summary(List<SummaryLine> netInputs, List<SummaryLine> netOutputs,
                      List<FluidSummaryLine> netFluidInputs, List<FluidSummaryLine> netFluidOutputs,
                      Map<RecipeProperty<?>, Long> propertyTotals) {

    public static class SummaryLine {

        public final ItemStack stack;
        public int totalCount;

        SummaryLine(ItemStack stack, int count) {
            this.stack = stack;
            this.totalCount = count;
        }
    }

    public static class FluidSummaryLine {

        public final FluidStack fluid;
        public int totalAmount;

        FluidSummaryLine(FluidStack fluid, int amount) {
            this.fluid = fluid;
            this.totalAmount = amount;
        }
    }

    static void mergeInto(List<SummaryLine> list, ItemStack stack, int count) {
        for (SummaryLine line : list) {
            if (line.stack.isItemEqual(stack)) {
                line.totalCount += count;
                return;
            }
        }
        list.add(new SummaryLine(stack, count));
    }

    static void mergeFluidInto(List<FluidSummaryLine> list, FluidStack fluid, int amount) {
        for (FluidSummaryLine line : list) {
            if (line.fluid.isFluidEqual(fluid)) {
                line.totalAmount += amount;
                return;
            }
        }
        list.add(new FluidSummaryLine(fluid.copy(), amount));
    }
}
