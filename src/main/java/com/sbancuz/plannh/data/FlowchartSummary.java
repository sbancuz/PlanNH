package com.sbancuz.plannh.data;

import java.util.List;

import net.minecraft.item.ItemStack;

public record FlowchartSummary(List<SummaryLine> netInputs, List<SummaryLine> netOutputs, long totalEu) {

    public static class SummaryLine {

        public final ItemStack stack;
        public int totalCount;

        SummaryLine(ItemStack stack, int count) {
            this.stack = stack;
            this.totalCount = count;
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
}
