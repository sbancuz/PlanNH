package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.data.flowchart.Summary.SummaryMode;

public class SlotSet {

    public static class Slot {

        @Nonnull
        public String name;
        @Nonnull
        public Graph graph;

        public Slot(final String name, final Graph graph) {
            this.name = name;
            this.graph = graph;
        }
    }

    public static final int DEFAULT_SUMMARY_X = 210;
    public static final int DEFAULT_SUMMARY_Y = 46;

    @Nonnull
    public final List<Slot> slots = new ArrayList<>();
    public int activeSlot = 0;
    public int summaryX = DEFAULT_SUMMARY_X;
    public int summaryY = DEFAULT_SUMMARY_Y;
    public boolean summaryCollapsed = false;
    public SummaryMode summaryMode = SummaryMode.CYCLES;

    @Nonnull
    public Graph getActiveGraph() {
        if (slots.isEmpty()) {
            slots.add(new Slot("Slot 1", new Graph()));
        }
        if (activeSlot < 0 || activeSlot >= slots.size()) {
            activeSlot = 0;
        }
        return slots.get(activeSlot).graph;
    }
}
