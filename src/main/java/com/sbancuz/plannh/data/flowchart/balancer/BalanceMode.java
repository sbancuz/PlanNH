package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.Locale;
import java.util.Set;

import net.minecraft.util.StatCollector;

import com.sbancuz.plannh.data.flowchart.balancer.stages.EveryMachineRuns;
import com.sbancuz.plannh.data.flowchart.balancer.stages.ExtentMinStage;
import com.sbancuz.plannh.data.flowchart.balancer.stages.ExternalMinStage;
import com.sbancuz.plannh.data.flowchart.balancer.stages.FastPathStage;
import com.sbancuz.plannh.data.flowchart.balancer.stages.FlowMinStage;
import com.sbancuz.plannh.data.flowchart.balancer.stages.GateCountStage;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * The definition of every balance mode: each constant IS the {@link Chain} that solves it, the
 * {@link Heuristics} its models are sized under, the pin kinds it honours, and whether it has a
 * choices surface ({@link #supportsAlternatives()}). Defining a new balancer is a new constant - the
 * stages run in order by the generic {@link Chain}, so the mode mapping is the whole difference
 * between them.
 */
public enum BalanceMode {

    /** NONE: no chain at all - the point the chart already holds, unchanged. Never run by {@link Balancer}. */
    NONE(Chain.empty(), Heuristics.counts(), Set.of(), false),

    /**
     * Output-priority: outputs shipped exactly, inputs at least, fewest extents.
     */
    OUTPUT(Chain.enter(new ExtentMinStage(false)), Heuristics.counts(), Set.of(Pin.FIXED_COUNT), false),

    /**
     * Input-priority: inputs consume exactly their capacity, outputs supply at least what's demanded.
     */
    INPUT(Chain.enter(new ExtentMinStage(true)), Heuristics.counts(), Set.of(Pin.FIXED_COUNT), false),

    /**
     * Lexicographic auto-balance: fractional machine counts, automatic source/sink placement on
     * connected ports, loops handled natively - the gate-count entry, the external-quantity and
     * internal-flow stages, the zero-gate fast path, and the "every machine runs" replay.
     */
    AUTO(Chain.enter(new GateCountStage())
        .then(new ExternalMinStage())
        .then(new FlowMinStage())
        .prelude(new FastPathStage())
        .audit(new EveryMachineRuns())
        .withPinDiagnosis(), Heuristics.auto(), Set.of(Pin.FIXED_COUNT, Pin.TARGET_RATE, Pin.EXTENT), true);

    @Getter
    @Accessors(fluent = true)
    private final Chain<?> chain;
    @Getter
    @Accessors(fluent = true)
    private final Heuristics heuristics;
    @Getter
    @Accessors(fluent = true)
    private final Set<Pin> pins;
    @Getter
    @Accessors(fluent = true)
    private final boolean supportsAlternatives;

    BalanceMode(final Chain<?> chain, final Heuristics heuristics, final Set<Pin> pins,
        final boolean supportsAlternatives) {
        this.chain = chain;
        this.heuristics = heuristics;
        this.pins = pins;
        this.supportsAlternatives = supportsAlternatives;
    }

    public String displayName() {
        return StatCollector.translateToLocal(
            "plannh.gui.balancer_mode." + this.name()
                .toLowerCase(Locale.ROOT));
    }
}
