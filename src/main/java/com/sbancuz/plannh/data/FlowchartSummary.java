package com.sbancuz.plannh.data;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.sbancuz.plannh.api.RecipePropertyAPI;

public record FlowchartSummary(List<SummaryLine> netInputs, List<SummaryLine> netOutputs,
    Map<RecipeProperty<?>, Long> propertyTotals) {

    public FlowchartSummary(List<SummaryLine> netInputs, List<SummaryLine> netOutputs, long totalEu) {
        this(netInputs, netOutputs, Map.of(RecipePropertyAPI.TOTAL_EU, totalEu));
    }

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
