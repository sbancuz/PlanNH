package com.sbancuz.plannh.gui.summary;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.EnumValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.FlowchartList;
import com.sbancuz.plannh.gui.PlannhColors;

/**
 * The summary HUD: a draggable, screen-fixed panel that reads the global {@link Summary} from
 * {@link Plan}. Position is saved in the Plan so it survives across sessions and graph switches.
 */
public class SummaryWidget extends ParentWidget<SummaryWidget> implements IDraggable {

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

    protected final CanvasWidget canvas;
    protected final Summary data;

    private Graph lastGraph;
    private boolean moving = false;
    private int dragStartMouseX, dragStartMouseY;
    private int dragStartX, dragStartY;

    public SummaryWidget(final CanvasWidget canvas) {
        this.canvas = canvas;
        this.data = Plan.getInstance()
            .getSummary();
        pos(data.getX(), data.getY());

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
            data.setSectionOrder(order);
        });

        for (final int ordinal : data.getSectionOrder()) {
            final Summary.Section section = Summary.Section.VALUES[ordinal];
            sectionsList.child(new SummarySection(this, section, sectionsList).marginBottom(SECTION_GAP));
        }

        child(
            SummaryFlow.col()
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
                    SummaryFlow.row()
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
                            SummaryFlow.row()
                                .coverChildren()
                                .childPadding(BUTTON_GAP)
                                .collapseDisabledChild()
                                .child(modeToggle())
                                .child(rateToggle())
                                .child(SummaryHeader.foldToggle(data, Summary.Section.ALL))))
                .child(
                    new Widget<>().fullWidth()
                        .height(1)
                        .background(new Rectangle().color(PlannhColors.SUMMARY_TITLE_LINE.getColor())))
                .child(sectionsList));
    }

    private static CycleButtonWidget rateToggle() {
        final Summary summary = Plan.getInstance()
            .getSummary();
        final CycleButtonWidget toggle = new CycleButtonWidget().size(SummaryHeader.HEADER_H, SummaryHeader.HEADER_H)
            .tooltipStatic(
                t -> t.addLine(IKey.lang("plannh.summary.rate.title"))
                    .addLine(IKey.lang("plannh.summary.mode.switch_hint")))
            .value(new EnumValue.Dynamic<>(Summary.RateUnit.class, summary::getRateUnit, summary::setRateUnit))
            .setEnabledIf(_ -> summary.getMode() == Summary.Mode.THROUGHPUT);
        for (final Summary.RateUnit unit : Summary.RateUnit.VALUES) {
            toggle.stateOverlay(unit, IKey.lang(unit.langKey));
        }
        return toggle;
    }

    private CycleButtonWidget modeToggle() {
        final Summary summary = Plan.getInstance()
            .getSummary();
        return new CycleButtonWidget().size(SummaryHeader.HEADER_H, SummaryHeader.HEADER_H)
            .tooltipStatic(
                t -> t.addLine(IKey.lang("plannh.summary.mode.title"))
                    .addLine(IKey.lang("plannh.summary.mode.switch_hint")))
            .value(new EnumValue.Dynamic<>(Summary.Mode.class, summary::getMode, val -> {
                summary.setMode(val);
                summary.recompute(canvas.getGraph());
            }))
            .stateOverlay(Summary.Mode.CYCLES, IKey.lang("plannh.summary.mode.cycles.short"))
            .stateOverlay(Summary.Mode.THROUGHPUT, IKey.lang("plannh.summary.mode.throughput.short"));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        final Graph current = canvas.getGraph();
        if (lastGraph != current) {
            lastGraph = current;
            data.recompute(current);
        }
    }

    protected void reposition() {
        pos(data.getX(), data.getY());
    }

    @Override
    public void drawMovingState(final ModularGuiContext context, final float partialTicks) {}

    @Override
    public boolean onDragStart(final int mouseButton) {
        if (mouseButton != 0) return false;
        dragStartMouseX = getContext().getAbsMouseX();
        dragStartMouseY = getContext().getAbsMouseY();
        dragStartX = data.getX();
        dragStartY = data.getY();
        return true;
    }

    @Override
    public void onDrag(final int mouseButton, final long timeSinceLastClick) {
        data.setX(dragStartX + (getContext().getAbsMouseX() - dragStartMouseX));
        data.setY(dragStartY + (getContext().getAbsMouseY() - dragStartMouseY));
        reposition();
    }

    @Override
    public void onDragEnd(final boolean successful) {
        if (!successful) {
            final Area a = canvas.getArea();
            final Area s = getArea();
            final int maxX = Math.max(a.rx, a.rx + a.width - s.width);
            final int maxY = Math.max(a.ry, a.ry + a.height - s.height);
            data.setX(Math.clamp(data.getX(), a.rx, maxX));
            data.setY(Math.clamp(data.getY(), a.ry, maxY));
            reposition();
        }
        PlanAPI.save();
    }

    @Override
    public boolean isMoving() {
        return moving;
    }

    @Override
    public void setMoving(final boolean moving) {
        this.moving = moving;
    }

    @Override
    public @Nullable Area getMovingArea() {
        return null;
    }

    @Override
    public boolean canDropHere(final int x, final int y, @Nullable final IWidget widget) {
        final Area a = canvas.getArea();
        final Area s = getArea();
        return data.getX() >= a.rx && data.getY() >= a.ry
            && data.getX() + s.width <= a.rx + a.width
            && data.getY() + s.height <= a.ry + a.height;
    }
}
