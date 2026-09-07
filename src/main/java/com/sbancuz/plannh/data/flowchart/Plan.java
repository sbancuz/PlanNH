package com.sbancuz.plannh.data.flowchart;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.sbancuz.plannh.api.PlanAPI;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Plan {

    /** The panel-wide display mode: aligned per-cycle totals or per-second rates. */
    public enum Mode {
        CYCLES,
        THROUGHPUT
    }

    /**
     * The time unit per-second rates are spelled in. {@code secondsPerUnit} rescales a rate that
     * is stored per second; the lang keys cover the button's short form and the row suffix.
     */
    public enum RateUnit {

        SECONDS("second", 1),
        MINUTES("minute", 60),
        HOURS("hour", 3600),
        DAYS("day", 86400);

        public static final RateUnit[] VALUES = RateUnit.values();

        public final String langKey;
        public final double secondsPerUnit;

        RateUnit(final String name, final double secondsPerUnit) {
            this.langKey = "plannh.summary.rate." + name;
            this.secondsPerUnit = secondsPerUnit;
        }

        public String suffixKey() {
            return langKey + ".suffix";
        }
    }

    @Nullable
    private static Plan INSTANCE;

    private transient final List<Graph> graphs = new ArrayList<>();
    private int activeIndex = 0;
    private boolean snapToGrid;

    private Mode mode = Mode.CYCLES;
    private RateUnit rateUnit = RateUnit.SECONDS;
    private int[] sectionOrder = defaultSectionOrder();

    /**
     * Bumped by every settings change; part of each summary's derived-cache key so a toggle
     * invalidates all charts at once without touching any {@code graph.version()}.
     */
    private transient long settingsVersion = 0;

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
        } catch (final Exception | LinkageError ignored) {
            // LinkageError as well as Exception: getSaveFile asks Minecraft where the instance dir is,
            // and off a client that class is not loadable at all - headlessly it raises
            // NoClassDefFoundError, which is an Error and so slips straight past a catch of Exception.
            // Every caller of this already wants "no save to read" here, so an empty plan is the
            // answer in both cases.
        }
        final Plan plan = new Plan();
        plan.getGraphs()
            .add(new Graph("Slot 1"));
        return plan;
    }

    public static void unloadPlan() {
        PlanAPI.save();
        INSTANCE = null;
    }

    public void setMode(final Mode mode) {
        this.mode = mode;
        settingsVersion++;
    }

    public void setRateUnit(final RateUnit rateUnit) {
        this.rateUnit = rateUnit;
        settingsVersion++;
    }

    public void setSectionOrder(final int[] sectionOrder) {
        this.sectionOrder = sectionOrder;
        settingsVersion++;
    }

    /**
     * The sanitized display order; a corrupt or missing array (old saves) repairs to the default
     * in place, so callers can keep the returned reference.
     */
    public int[] getSectionOrder() {
        final int n = Summary.Section.VALUES.length;
        boolean valid = sectionOrder != null && sectionOrder.length == n;
        if (valid) {
            final boolean[] seen = new boolean[n];
            for (final int ordinal : sectionOrder) {
                if (ordinal < 0 || ordinal >= n || seen[ordinal]) {
                    valid = false;
                    break;
                }
                seen[ordinal] = true;
            }
        }
        if (!valid) sectionOrder = defaultSectionOrder();
        return sectionOrder;
    }

    private static int[] defaultSectionOrder() {
        return Arrays.stream(Summary.Section.VALUES)
            .mapToInt(Summary.Section::ordinal)
            .toArray();
    }
}
