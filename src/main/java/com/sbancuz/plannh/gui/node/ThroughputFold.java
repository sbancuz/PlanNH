package com.sbancuz.plannh.gui.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;
import com.sbancuz.plannh.gui.GuiHelper;
import com.sbancuz.plannh.gui.GuiHelper.RateUnit;
import com.sbancuz.plannh.gui.PlannhColors;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartTextWidget;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.common.IFlowchartDraggable;
import com.sbancuz.plannh.gui.tooltips.TooltipBuilder;

class ThroughputFold extends NodeFold {

    private static final String LANG = "plannh.gui.node.throughput.";

    /** The square that opens a row. Big enough to read a hue at, small enough not to crowd the name. */
    private static final int PIN = 7;

    private final Node data;

    /** The rows carrying a rate, so a change of shift can reach the tooltips they own. */
    private final List<Flow> rateRows = new ArrayList<>();

    private long lastVersion = Long.MIN_VALUE;
    private RateUnit lastUnit;
    private boolean lastShift;

    public ThroughputFold(final NodeWidget node) {
        super(
            node,
            () -> node.getData()
                .isThroughputOpen());
        name("fold.throughput");

        data = node.getData();
        lastVersion = version();
        lastUnit = rateUnit();
        lastShift = GuiHelper.shiftHeld();

        onUpdateListener(w -> {
            // A rate is a function of the chart's last solve, not of the recipe, so the version that
            // solve bumped is what has to be watched here. The unit is the reader's choice and turns
            // every row's value over; shift only changes what the rows say when hovered.
            final long current = version();
            final RateUnit unit = rateUnit();
            final boolean shift = GuiHelper.shiftHeld();
            if (current != lastVersion || unit != lastUnit) {
                lastVersion = current;
                lastUnit = unit;
                markStale();
            }
            if (shift != lastShift) {
                lastShift = shift;
                rateRows.forEach(Flow::markTooltipDirty);
            }
        }, true);
    }

    @Override
    protected void rebuild() {
        removeAll();
        rateRows.clear();

        final Balancer.NodeBalance balance = node.balance();
        final int duration = balance == null ? 0 : balance.durationPerOp();
        if (duration <= 0) {
            child(hint(LANG + "unbalanced"));
            scheduleResize();
            return;
        }

        final RateUnit unit = rateUnit();
        rates(true, data.getInputs(), balance.effectiveInputs(), duration, unit);
        rates(false, data.getOutputs(), balance.effectiveOutputs(), duration, unit);

        // Solved, and still nothing to say: a machine with no port the solve moved has no rate to
        // print, which is a different thing from not having been solved.
        if (getChildren().isEmpty()) child(hint(LANG + "unplanned"));
        scheduleResize();
    }

    /** One row per port moving this way, and nothing at all when none is. */
    private void rates(final boolean input, final List<Port<?>> ports, final Map<Integer, Float> perCycle,
        final int duration, final RateUnit unit) {
        for (int i = 0; i < ports.size(); i++) {
            final Float rate = perCycle.get(i);
            if (rate == null || rate <= 0) continue;

            final Flow row = rateRow(ports.get(i), i, input, rate, duration, unit);
            rateRows.add(row);
            child(row);
        }
    }

    private Flow rateRow(final Port<?> port, final int portIndex, final boolean input, final float perCycle,
        final int duration, final RateUnit unit) {
        // The summary's shape: the bar, the name and the value are siblings in one row. The bar is
        // pixels, so it takes what it takes, and the two texts are given caps out of the rest - a name
        // wrapped at its own cap measures to the height it is drawn at, whatever the row turns out to
        // be. Nothing is nested inside the name either: a width handed down through two rows is a
        // width that is not there yet.
        return FlowchartFlow.row(node)
            .name("throughput.rate")
            .fullWidth()
            .marginBottom(ROW_GAP)
            .coverChildrenHeight()
            .childPadding(2)
            .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
            .tooltipBuilder(tooltip -> writeTooltip(tooltip, port, portIndex, input, perCycle, duration))
            .child(
                FlowchartFlow.row(node)
                    .widthRel(LABEL_RATIO)
                    .crossAxisAlignment(Alignment.CrossAxis.START)
                    .coverChildrenHeight()
                    .childPadding(2)
                    .child(new SquareIndicator(node, port, input))
                    .child(new FlowchartTextWidget(IKey.str(port.getDisplayName()), node).textAlign(Alignment.CenterLeft)))
            .child(
                new FlowchartTextWidget(
                    IKey.str(PortWidget.rateText(port, perCycle, duration, unit)), node)
            .color(input ? PlannhColors.ACCENT_RED2.getColor() : PlannhColors.ACCENT_GREEN3.getColor())
            .widthRel(1 - LABEL_RATIO)
                        .textAlign(Alignment.CenterRight));
    }

    private void writeTooltip(final RichTooltip tooltip, final Port<?> port, final int portIndex, final boolean input,
        final float perCycle, final int duration) {
        tooltip.addFromItem(
            port.getAllStacks()
                .getFirst().item);

        final TooltipBuilder out = TooltipBuilder.create(tooltip);
        PortWidget.writeThroughput(out, data, port, input, portIndex, perCycle, duration);
        out.flush();
    }

    private long version() {
        return node.getCanvas()
            .getGraph()
            .getVersion();
    }

    private static RateUnit rateUnit() {
        return Plan.getInstance()
            .getSummary()
            .getRateUnit();
    }

    private static final class SquareIndicator extends Widget<SquareIndicator> implements IFlowchartDraggable {

        private final NodeWidget node;

        private SquareIndicator(final NodeWidget node, final Port<?> port, final boolean input) {
            this.node = node;
            name("throughput.rate.pin");
            width(PIN);
            fullHeight();

            background(
                new Rectangle().color(port.getPinColor(input)),
                new Rectangle().hollow()
                    .color(PlannhColors.SUMMARY_BG.getColor()));
        }

        @Override
        public FlowchartWidget<?, ?> getFlowchartParent() {
            return node;
        }
    }
}
