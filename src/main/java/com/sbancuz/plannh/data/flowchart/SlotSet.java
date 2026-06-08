package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;

public class SlotSet {

    public static class Slot {

        public String name;
        public Graph graph;

        public Slot(String name, Graph graph) {
            this.name = name;
            this.graph = graph;
        }
    }

    public final List<Slot> slots = new ArrayList<>();
    public int activeSlot = 0;

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
