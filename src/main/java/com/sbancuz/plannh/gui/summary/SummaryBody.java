package com.sbancuz.plannh.gui.summary;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.data.flowchart.Summary.Line;
import com.sbancuz.plannh.data.flowchart.balancer.Severity;
import com.sbancuz.plannh.gui.FlowchartFlow;
import com.sbancuz.plannh.gui.FlowchartWidget;
import com.sbancuz.plannh.gui.PlannhColors;

/**
 * The rows of one section as a vertical list of real MUI2 widgets, one per {@link Line}: hover,
 * tooltips and clicks ride MUI2's own hit-testing instead of row math. When the summary recomputes
 * the rows are swapped out wholesale (the same child-subtree swap {@code DynamicSyncedWidget} does),
 * which lets the layout engine re-sizes the panel through its ordinary dirty chain. The body covers
 * its children so the section always grows to exactly its rows.
 */
class SummaryBody extends FlowchartFlow {

    protected static final int LINE_H = 13;
    protected static final int TEXT_X = 12;

    private final Summary data;
    private final Graph graph;
    private final Summary.Section section;
    private long rowsBuiltAt = Long.MIN_VALUE;
    private Plan.Mode rowsMode = null;
    private Plan.RateUnit rowsUnit = Plan.RateUnit.SECONDS;

    SummaryBody(final FlowchartWidget<?, ?> panel, final Summary data, final Graph graph,
        final Summary.Section section) {
        super(GuiAxis.Y, panel);
        this.data = data;
        this.graph = graph;
        this.section = section;
        final Plan plan = Plan.getInstance();
        this.rowsMode = data.computedMode();
        this.rowsUnit = plan.getRateUnit();

        fullWidth().coverChildrenHeight()
            .setEnabledIf(_ -> !data.isSummaryFold(section));

        rebuildRows();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        // Reload both when the chart moves and when the plan-wide throughput/cycles mode or rate
        // unit changes (which re-derive the rows but not the graph version).
        final Plan plan = Plan.getInstance();
        final Plan.Mode mode = data.computedMode();
        final Plan.RateUnit unit = plan.getRateUnit();
        if (rowsBuiltAt != data.calculatedAt() || rowsMode != mode || rowsUnit != unit) {
            rowsBuiltAt = data.calculatedAt();
            rowsMode = mode;
            rowsUnit = unit;
            rebuildRows();
        }
    }

    /** Swap the whole row list for the summary's current lines; child churn re-sizes the panel. */
    private void rebuildRows() {
        removeAll();
        for (final Line<?> line : data.lines(section)) {
            if (line instanceof Line.Totals) {
                child(
                    new Widget<>().fullWidth()
                        .height(1)
                        .background(new Rectangle().color(PlannhColors.SEPARATOR_DIM.getColor())));
            }
            child(row(line));
        }
        scheduleResize();
    }

    private Widget<?> row(final Line<?> line) {
        final FlowchartWidget<?, ?> panel = getFlowchartParent();
        return switch (line) {
            case Line.Measure<?> measure -> new MeasureRow(panel, measure, rowSuffix(), amountScale());
            case Line.Message message -> new TextRow(panel, IKey.str(message.displayName()),
                severityColor(message.note().severity()));
            case Line.Text(String key) -> new TextRow(panel, IKey.lang(key), PlannhColors.SUMMARY_TEXT.getColor());
            case Line.Heading heading -> new TextRow(panel, IKey.str(heading.displayName()),
                PlannhColors.SUMMARY_TEXT_MUTED.getColor());
            case Line.Choice choice -> new ChoiceRow(panel, graph, choice);
            case Line.Totals totals -> new TotalsRow(panel, totals, rowsMode);
        };
    }

    private static int severityColor(final Severity severity) {
        return switch (severity) {
            case ERROR -> PlannhColors.ACCENT_RED.getColor();
            case WARN -> PlannhColors.ACCENT_AMBER.getColor();
            case INFO -> PlannhColors.ACCENT_BLUE.getColor();
        };
    }

    private String rowSuffix() {
        if (!isRateSection() || rowsMode != Plan.Mode.THROUGHPUT) {
            return " x";
        }
        return StatCollector.translateToLocal(rowsUnit.suffixKey());
    }

    /** Only outputs and inputs carry rates; machine counts and properties are per-cycle totals. */
    private boolean isRateSection() {
        return section == Summary.Section.OUTPUTS || section == Summary.Section.INPUTS;
    }

    private double amountScale() {
        return isRateSection() && rowsMode == Plan.Mode.THROUGHPUT ? rowsUnit.secondsPerUnit : 1.0;
    }

}
