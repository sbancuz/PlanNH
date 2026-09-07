package com.sbancuz.plannh.gui.summary;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.EnumValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.FlowchartFlow;
import com.sbancuz.plannh.gui.FlowchartList;
import com.sbancuz.plannh.gui.FlowchartWidget;
import com.sbancuz.plannh.gui.PlannhColors;

/**
 * The summary panel: a draggable canvas widget that reads one chart's {@link Summary} - a title
 * bar with the master collapse (fold {@link Summary.Section#ALL}), then one {@link SummarySection} per
 * content bucket in the chart's stored display order. It never re-sorts and never runs a solver;
 * every row was decided by {@link Summary#recompute}. When a newer solve moves the summary it just
 * re-lays-out.
 */
public class SummaryWidget extends FlowchartWidget<SummaryWidget, Summary> {

    private static final int WIDTH = 200;
    private static final int PADDING = 4;
    private static final int CORNER_RADIUS = 2;
    private static final int SECTION_GAP = 4;
    private static final int TITLE_INSET_X = 4;
    private static final int BUTTON_GAP = 2;
    private static final int SCROLLBAR_GAP = 4;
    private static final int SCREEN_MARGIN = 10;
    private static final int MIN_VIEWPORT_H = 45;
    private static final int PANEL_CHROME = SummaryHeader.HEADER_H + 1 + SECTION_GAP * 4 + SCREEN_MARGIN;

    public SummaryWidget(final CanvasWidget canvas, final Summary data) {
        super(canvas, data);

        coverChildren();

        final FlowchartList sectionsList = new FlowchartList().fullWidth()
            .paddingRight(SCROLLBAR_GAP)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .scrollDirection(new VerticalScrollData())
            .setEnabledIf(_ -> !data.isSummaryFold(Summary.Section.ALL))
            .maxSize(
                () -> Math.max(
                    MIN_VIEWPORT_H,
                    this.canvas.getArea().height / this.canvas.getGraph()
                        .getZoom() - PANEL_CHROME));

        sectionsList.onMove(children -> {
            final int[] order = new int[children.size()];
            for (int i = 0; i < order.length; i++) {
                order[i] = ((SummarySection) children.get(i)).section()
                    .ordinal();
            }
            Plan.getInstance()
                .setSectionOrder(order);
        });

        for (final int ordinal : Plan.getInstance()
            .getSectionOrder()) {
            final Summary.Section section = Summary.Section.VALUES[ordinal];
            sectionsList.child(new SummarySection(this, section, sectionsList).marginBottom(SECTION_GAP));
        }

        child(
            FlowchartFlow.col(this)
                .width(WIDTH)
                .padding(PADDING)
                .childPadding(SECTION_GAP)
                .coverChildrenHeight()
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .collapseDisabledChild()
                .background(
                    new Rectangle().color(PlannhColors.SUMMARY_BG.getColor()),
                    new Rectangle().cornerRadius(CORNER_RADIUS)
                        .hollow(1)
                        .color(PlannhColors.SUMMARY_BORDER.getColor()))
                .child(
                    FlowchartFlow.row(this)
                        .fullWidth()
                        .height(SummaryHeader.HEADER_H)
                        .paddingLeft(TITLE_INSET_X)
                        .childPadding(PADDING)
                        .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                        .background(new Rectangle().color(PlannhColors.SUMMARY_TITLE_BG.getColor()))
                        .child(
                            new TextWidget<>(IKey.lang(Summary.Section.ALL.titleKey()))
                                .color(PlannhColors.TEXT_WHITE.getColor()))
                        .child(
                            FlowchartFlow.row(this)
                                .coverChildren()
                                .childPadding(BUTTON_GAP)
                                .collapseDisabledChild()
                                .child(modeToggle(this))
                                .child(rateToggle())
                                .child(SummaryHeader.foldToggle(data, Summary.Section.ALL))))
                .child(
                    new Widget<>().fullWidth()
                        .height(1)
                        .background(new Rectangle().color(PlannhColors.SUMMARY_TITLE_LINE.getColor())))
                .child(sectionsList));
    }

    private static CycleButtonWidget rateToggle() {
        final Plan plan = Plan.getInstance();
        final CycleButtonWidget toggle = new CycleButtonWidget().size(SummaryHeader.HEADER_H, SummaryHeader.HEADER_H)
            .tooltipStatic(
                t -> t.addLine(IKey.lang("plannh.summary.rate.title"))
                    .addLine(IKey.lang("plannh.summary.mode.switch_hint")))
            .value(new EnumValue.Dynamic<>(Plan.RateUnit.class, plan::getRateUnit, plan::setRateUnit))
            .setEnabledIf(_ -> plan.getMode() == Plan.Mode.THROUGHPUT);
        for (final Plan.RateUnit unit : Plan.RateUnit.VALUES) {
            toggle.stateOverlay(unit, IKey.lang(unit.langKey));
        }
        return toggle;
    }

    private CycleButtonWidget modeToggle(final FlowchartWidget<?, ?> panel) {
        return new CycleButtonWidget().size(SummaryHeader.HEADER_H, SummaryHeader.HEADER_H)
            .tooltipStatic(
                t -> t.addLine(IKey.lang("plannh.summary.mode.title"))
                    .addLine(IKey.lang("plannh.summary.mode.switch_hint")))
            .value(new EnumValue.Dynamic<>(Plan.Mode.class, Plan.getInstance()::getMode, val -> {
                Plan.getInstance()
                    .setMode(val);
                data.recompute(
                    panel.getCanvas()
                        .getGraph());
            }))
            .stateOverlay(Plan.Mode.CYCLES, IKey.lang("plannh.summary.mode.cycles.short"))
            .stateOverlay(Plan.Mode.THROUGHPUT, IKey.lang("plannh.summary.mode.throughput.short"));
    }
}
