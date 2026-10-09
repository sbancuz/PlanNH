package com.sbancuz.plannh.gui.summary;

import java.util.Locale;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.data.flowchart.Summary.Line;
import com.sbancuz.plannh.data.flowchart.balancer.Severity;
import com.sbancuz.plannh.gui.GuiHelper;
import com.sbancuz.plannh.gui.PlannhColors;

/**
 * The rows of one section as a vertical list of real MUI2 widgets, one per {@link Line}: hover,
 * tooltips and clicks ride MUI2's own hit-testing instead of row math. When the summary recomputes
 * the rows are swapped out wholesale (the same child-subtree swap {@code DynamicSyncedWidget} does),
 * which lets the layout engine re-sizes the panel through its ordinary dirty chain. The body covers
 * its children so the section always grows to exactly its rows.
 */
class SummaryBody extends SummaryFlow {

    protected static final int LINE_H = 13;
    protected static final int TEXT_X = 12;

    private final Summary data;
    private final Summary.Section section;
    private long rowsBuiltAt = Long.MIN_VALUE;
    private Graph rowsFor = null;
    private Summary.Mode rowsMode = null;
    private GuiHelper.RateUnit rowsUnit = GuiHelper.RateUnit.SECONDS;

    SummaryBody(final SummaryWidget panel, final Summary data, final Summary.Section section) {
        super(GuiAxis.Y);
        this.data = data;
        this.section = section;
        this.rowsMode = data.computedMode();
        this.rowsUnit = data.getRateUnit();

        fullWidth().coverChildrenHeight()
            .setEnabledIf(_ -> !data.isSummaryFold(section));

        rebuildRows(panel);
    }

    /** Named for the section it holds, so the debug overlay says which body is under the mouse. */
    @Override
    public String getName() {
        return "summary.body." + section.name()
            .toLowerCase(Locale.ROOT);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        final Summary.Mode mode = data.computedMode();
        final GuiHelper.RateUnit unit = data.getRateUnit();
        final Graph forGraph = data.computedFor();
        if (rowsBuiltAt != data.calculatedAt() || rowsFor != forGraph || rowsMode != mode || rowsUnit != unit) {
            rowsBuiltAt = data.calculatedAt();
            rowsFor = forGraph;
            rowsMode = mode;
            rowsUnit = unit;
            rebuildRows(null);
        }
    }

    private void rebuildRows(final SummaryWidget panel) {
        removeAll();
        for (final Line<?> line : data.lines(section)) {
            if (line instanceof Line.Totals) {
                child(
                    new Widget<>().name("summary.rule")
                        .fullWidth()
                        .height(1)
                        .background(new Rectangle().color(PlannhColors.SEPARATOR_DIM.getColor())));
            }
            child(row(line, panel));
        }
        scheduleResize();
    }

    private Widget<?> row(final Line<?> line, final SummaryWidget panel) {
        return switch (line) {
            case Line.Measure<?> measure -> new MeasureRow(measure, rowSuffix(), amountScale());
            case Line.Message message -> new TextRow(IKey.str(message.displayName()),
                severityColor(message.note().severity()), "summary.message");
            case Line.Text(String key) -> new TextRow(IKey.lang(key), PlannhColors.SUMMARY_TEXT.getColor(),
                "summary.text" );
            case Line.Heading heading -> new TextRow(IKey.str(heading.displayName()),
                PlannhColors.SUMMARY_TEXT_MUTED.getColor(), "summary.heading" );
            case Line.Choice choice -> new ChoiceRow(choice).name("summary.choice" );
            case Line.Totals totals -> new TotalsRow(totals, rowsMode).name("summary.totals");
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
        if (!isRateSection() || rowsMode != Summary.Mode.THROUGHPUT) {
            return " x";
        }
        return StatCollector.translateToLocal(rowsUnit.suffixKey());
    }

    private boolean isRateSection() {
        return section == Summary.Section.OUTPUTS || section == Summary.Section.INPUTS;
    }

    private double amountScale() {
        return isRateSection() && rowsMode == Summary.Mode.THROUGHPUT ? rowsUnit.secondsPerUnit : 1.0;
    }
}
