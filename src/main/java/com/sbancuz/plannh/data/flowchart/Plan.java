package com.sbancuz.plannh.data.flowchart;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Summary.SummaryMode;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Plan {

    private static final int DEFAULT_SUMMARY_X = 210;
    private static final int DEFAULT_SUMMARY_Y = 46;

    @Nullable
    private static Plan INSTANCE;

    private transient final List<Graph> graphs = new ArrayList<>();
    private int activeIndex = 0;
    private int summaryX = DEFAULT_SUMMARY_X;
    private int summaryY = DEFAULT_SUMMARY_Y;
    private boolean summaryCollapsed = false;
    private SummaryMode summaryMode = SummaryMode.CYCLES;
    private boolean snapToGrid;

    private Plan() {}

    public static Plan getInstance() {
        if (INSTANCE == null) {
            INSTANCE = loadPlan();
        }
        return INSTANCE;
    }

    public static Graph getActiveGraph() {
        Plan plan = getInstance();
        if (plan.graphs.isEmpty()) {
            plan.graphs.add(new Graph("Slot 1"));
        }
        if (plan.activeIndex < 0 || plan.activeIndex >= plan.graphs.size()) {
            plan.activeIndex = 0;
        }
        return plan.graphs.get(plan.activeIndex);
    }

    private static Plan loadPlan() {
        try {
            final File saveFile = PlanAPI.getSaveFile();
            if (saveFile.isFile()) {
                final String data = Files.readString(saveFile.toPath(), StandardCharsets.UTF_8);
                return Serializer.decodePlan(data);
            }
        } catch (final Exception ignored) {}
        final Plan plan = new Plan();
        plan.getGraphs()
            .add(new Graph("Slot 1"));
        return plan;
    }
}
