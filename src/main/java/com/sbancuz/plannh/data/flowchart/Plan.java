package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;

import com.sbancuz.plannh.data.flowchart.Summary.SummaryMode;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Plan {

    private static final int DEFAULT_SUMMARY_X = 210;
    private static final int DEFAULT_SUMMARY_Y = 46;

    private transient final List<Graph> graphs = new ArrayList<>();
    private int activeIndex = 0;
    private int summaryX = DEFAULT_SUMMARY_X;
    private int summaryY = DEFAULT_SUMMARY_Y;
    private boolean summaryCollapsed = false;
    private SummaryMode summaryMode = SummaryMode.CYCLES;
    private boolean snapToGrid;

    public Graph getActiveGraph() {
        if (graphs.isEmpty()) {
            graphs.add(new Graph("Slot 1"));
        }
        if (activeIndex < 0 || activeIndex >= graphs.size()) {
            activeIndex = 0;
        }
        return graphs.get(activeIndex);
    }
}
