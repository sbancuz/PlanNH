package com.sbancuz.plannh.data.flowchart;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.sbancuz.plannh.data.RecipeProperty;

public record Summary(List<SummaryLine> netInputs, List<SummaryLine> netOutputs, List<FluidSummaryLine> netFluidInputs,
    List<FluidSummaryLine> netFluidOutputs, Map<RecipeProperty<?>, Long> propertyTotals) {

    public static class SummaryLine {

        public final ItemStack stack;
        public int totalCount;

        SummaryLine(final ItemStack stack, final int count) {
            this.stack = stack;
            this.totalCount = count;
        }
    }

    public static class FluidSummaryLine {

        public final FluidStack fluid;
        public int totalAmount;

        FluidSummaryLine(final FluidStack fluid, final int amount) {
            this.fluid = fluid;
            this.totalAmount = amount;
        }
    }

    static void mergeInto(final List<SummaryLine> list, final ItemStack stack, final int count) {
        for (final SummaryLine line : list) {
            if (line.stack.isItemEqual(stack)) {
                line.totalCount += count;
                return;
            }
        }
        list.add(new SummaryLine(stack, count));
    }

    static void mergeFluidInto(final List<FluidSummaryLine> list, final FluidStack fluid, final int amount) {
        for (final FluidSummaryLine line : list) {
            if (line.fluid.isFluidEqual(fluid)) {
                line.totalAmount += amount;
                return;
            }
        }
        list.add(new FluidSummaryLine(fluid.copy(), amount));
    }
}
