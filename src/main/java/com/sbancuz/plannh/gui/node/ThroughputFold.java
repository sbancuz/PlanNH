package com.sbancuz.plannh.gui.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
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
import com.sbancuz.plannh.gui.tooltips.TooltipTheme;

class ThroughputFold extends NodeFold {

    private static final String LANG = "plannh.gui.node.throughput.";
    private static final String PORT_LANG = "plannh.gui.node.port.";

    /** The square that opens a row. Big enough to read a hue at, small enough not to crowd the name. */
    private static final int PIN = 7;

    private static final float NAME_RATIO = 2 / 3f;

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
        rates(false, data.getOutputs(), balance.effectiveOutputs(), duration, unit);
        rates(true, data.getInputs(), balance.effectiveInputs(), duration, unit);

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

            final Flow row = rateRow(ports.get(i), input, rate, duration, unit);
            rateRows.add(row);
            child(row);
        }
    }

    private Flow rateRow(final Port<?> port, final boolean input, final float perCycle, final int duration,
        final RateUnit unit) {
        return FlowchartFlow.row(node)
            .fullWidth()
            .marginBottom(ROW_GAP)
            .coverChildrenHeight()
            .childPadding(2)
            .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
            .tooltipBuilder(tooltip -> writeTooltip(tooltip, port, perCycle, duration))
            // The name is given a width rather than left to take what it wants, which is the only
            // thing that makes it wrap: a text widget whose width is known draws itself inside that
            // width over as many lines as it needs, and one left to size itself never wraps at all.
            .child(
                FlowchartFlow.row(node)
                    .widthRel(NAME_RATIO)
                    .crossAxisAlignment(Alignment.CrossAxis.START)
                    .coverChildrenHeight()
                    .childPadding(2)
                    .child(new SquareIndicator(node, port, input))
                    .child(new FlowchartTextWidget(IKey.str(port.getDisplayName()), node)))
            .child(
                new FlowchartTextWidget(
                    IKey.str(rateText(port, perCycle, duration, unit))
                        .color(PlannhColors.ACCENT_CYAN2.getColor()),
                    node).widthRel(1 - NAME_RATIO)
                        .textAlign(Alignment.CenterRight));
    }

    private void writeTooltip(final RichTooltip tooltip, final Port<?> port, final float perCycle, final int duration) {
        final TooltipBuilder out = TooltipBuilder.create(tooltip);
        out.entry(
            IKey.lang(PORT_LANG + "rate"),
            TooltipTheme.Role.RATE,
            IKey.str(rateText(port, perCycle, duration, rateUnit())));

        if (GuiHelper.shiftHeld()) {
            out.group(PORT_LANG + "per_unit", rows -> {
                for (final RateUnit unit : RateUnit.VALUES) {
                    rows.entry(IKey.EMPTY, TooltipTheme.Role.RATE, IKey.str(rateText(port, perCycle, duration, unit)));
                }
            });
        } else out.langRow(PORT_LANG + "shift_hint");
        out.flush();
    }

    private IWidget hint(final String key) {
        return FlowchartFlow.row(node)
            .fullWidth()
            .coverChildrenHeight()
            .padding(2)
            .child(
                new FlowchartTextWidget(
                    IKey.lang(key)
                        .color(PlannhColors.TEXT_DIM.getColor()),
                    node).maxWidth(176 - 2 * 5 - 4));
    }

    /** This port's rate in the given unit, with the unit's suffix, the way every rate in the pack reads. */
    private static String rateText(final Port<?> port, final float perCycle, final int duration, final RateUnit unit) {
        return TooltipBuilder.rate(
            port.getType()
                .formatAmount(GuiHelper.rate(perCycle, duration, unit)),
            unit);
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
