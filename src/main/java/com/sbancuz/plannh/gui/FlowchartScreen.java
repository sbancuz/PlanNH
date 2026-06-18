package com.sbancuz.plannh.gui;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.Balancer.NodeBalance;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.SlotSet;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.data.flowchart.Summary.SummaryMode;

public class FlowchartScreen extends ModularScreen {

    private static final int LEFT_MARGIN = 5;
    private static final int RIGHT_MARGIN = 190;
    private static final int TOP_MARGIN = 30;
    private static final int BOTTOM_MARGIN = 30;

    @Nonnull
    public final Graph graph;
    @Nonnull
    public final CanvasWidget canvas;

    private FlowchartScreen(final ModularPanel panel, final Graph graph, final CanvasWidget canvas) {
        super(PlanNH.MODID, panel);
        getContext().setSettings(new UISettings());
        getContext().getUISettings()
            .getRecipeViewerSettings()
            .enable();
        this.graph = graph;
        this.canvas = canvas;
    }

    public static FlowchartScreen create() {
        final Graph graph = PlanAPI.getActiveGraph();
        final CanvasWidget canvas = new CanvasWidget(graph);

        final ModularPanel panel = ModularPanel.defaultPanel("flowchart_main")
            .fullScreenInvisible()
            .margin(LEFT_MARGIN, RIGHT_MARGIN, TOP_MARGIN, BOTTOM_MARGIN);

        final Flow mainColumn = Flow.column()
            .full();

        mainColumn.child(
            Flow.row()
                .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                .coverChildrenHeight()
                .fullWidth()
                .child(
                    Flow.row()
                        .coverChildren()
                        .childPadding(2)
                        .child(new ButtonWidget<>())
                        .child(
                            IKey.str("Slot x")
                                .asWidget())
                        .child(new ButtonWidget<>())))
            .child(canvas);

        canvas.child(new NoteWidget2(canvas));

        panel.child(mainColumn);
        panel.child(new SummaryWidget(canvas));

        return new FlowchartScreen(panel, graph, canvas);
    }

    @Override
    public void onResize(final int width, final int height) {
        super.onResize(width, height);
        if (getScreenWrapper() != null
            && getScreenWrapper().getGuiScreen() instanceof final FlowchartGuiContainer container) {
            container.applyNeiSizing(width);
        }
    }

    @Override
    public void onClose() {
        PlanAPI.save();
        super.onClose();
    }

    @Override
    public boolean onKeyPressed(final char typedChar, final int keyCode) {
        if (canvas.onKeyPressed(typedChar, keyCode) == Interactable.Result.SUCCESS) {
            return true;
        }
        return super.onKeyPressed(typedChar, keyCode);
    }

    private static class SlotBarWidget extends Widget<SlotBarWidget> implements Interactable {

        private final CanvasWidget canvas;
        private final List<ClickZone> zones = new ArrayList<>();

        private static final int TEXT_Y = 4;
        private static final int ARROW_W = 14;
        private static final int PREV_X = 4;
        private static final int NAME_GAP = 16;
        private static final int NAME_EST_W = 6;
        private static final int NAME_EST_PAD = 8;
        private static final int ADD_BTN_RIGHT = 30;
        private static final int DEL_BTN_RIGHT = 15;
        private static final int MODE_BTN_RIGHT = 86;
        private static final int SUMMARY_BTN_RIGHT = 72;
        private static final int GROUP_BTN_RIGHT = 58;
        private static final int NOTE_BTN_RIGHT = 44;

        SlotBarWidget(final CanvasWidget canvas) {
            this.canvas = canvas;
            pos(0, 0);
        }

        private record ClickZone(int ux1, int uy1, int ux2, int uy2, Runnable action) {

            boolean contains(final int ux, final int uy) {
                return ux >= ux1 && ux < ux2 && uy >= uy1 && uy < uy2;
            }
        }

        @Override
        public void draw(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
            final Area a = getArea();
            final int w = a.width;
            final int h = a.height;

            GuiDraw.drawRect(0, 0, w, h, PlannhColors.SLOT_BAR_BG.getColor());
            GuiDraw.drawRect(0, h - 1, w, 1, PlannhColors.SLOT_BAR_LINE.getColor());

            final SlotSet set = PlanAPI.getSlotSet();
            final String name = set.activeSlot >= 0 && set.activeSlot < set.slots.size()
                ? set.slots.get(set.activeSlot).name
                : "?";

            zones.clear();

            final int px = PREV_X;
            GuiDraw.drawText("<", px, TEXT_Y, 1.0f, PlannhColors.ACCENT_BLUE.getColor(), false);
            zones.add(new ClickZone(px, 0, px + ARROW_W, h, () -> shiftSlot(-1)));
            final int nameX = px + NAME_GAP;
            GuiDraw.drawText(name, nameX, TEXT_Y, 1.0f, PlannhColors.TEXT_WHITE.getColor(), false);
            final int nameW = name.length() * NAME_EST_W + NAME_EST_PAD;
            final int nbx = nameX + nameW;
            GuiDraw.drawText(">", nbx, TEXT_Y, 1.0f, PlannhColors.ACCENT_BLUE.getColor(), false);
            zones.add(new ClickZone(nbx, 0, nbx + ARROW_W, h, () -> shiftSlot(1)));

            final int ax = w - ADD_BTN_RIGHT;
            GuiDraw.drawText("+", ax, TEXT_Y, 1.0f, PlannhColors.ACCENT_GREEN.getColor(), false);
            zones.add(new ClickZone(ax, 0, ax + ARROW_W, h, this::addSlot));

            final int dx = w - DEL_BTN_RIGHT;
            GuiDraw.drawText("\u00d7", dx, TEXT_Y, 1.0f, PlannhColors.ACCENT_RED.getColor(), false);
            zones.add(new ClickZone(dx, 0, dx + ARROW_W, h, this::deleteSlot));

            final BalanceMode mode = canvas.getGraph()
                .getBalanceMode();
            final String modeLabel = switch (mode) {
                case NONE -> "M:-";
                case FORWARD -> "M:F";
                case BACKWARD -> "M:B";
            };
            final int modeColor = switch (mode) {
                case NONE -> PlannhColors.TEXT_MUTED.getColor();
                case FORWARD -> PlannhColors.ACCENT_GREEN.getColor();
                case BACKWARD -> PlannhColors.ACCENT_BLUE.getColor();
            };
            final int mx = w - MODE_BTN_RIGHT;
            GuiDraw.drawText(modeLabel, mx, TEXT_Y, 1.0f, modeColor, false);
            zones.add(new ClickZone(mx, 0, mx + ARROW_W, h, this::cycleBalanceMode));

            final SummaryMode sMode = set.summaryMode;
            final String sLabel = sMode == SummaryMode.CYCLES ? "S:C" : "S:T";
            final int sCol = sMode == SummaryMode.CYCLES ? PlannhColors.ACCENT_BLUE.getColor()
                : PlannhColors.ACCENT_GREEN.getColor();
            final int sx = w - SUMMARY_BTN_RIGHT;
            GuiDraw.drawText(sLabel, sx, TEXT_Y, 1.0f, sCol, false);
            zones.add(new ClickZone(sx, 0, sx + ARROW_W, h, () -> {
                final SlotSet s = PlanAPI.getSlotSet();
                s.summaryMode = s.summaryMode == SummaryMode.CYCLES ? SummaryMode.THROUGHPUT : SummaryMode.CYCLES;
                PlanAPI.save();
            }));

            final int gx = w - GROUP_BTN_RIGHT;
            GuiDraw.drawText("G", gx, TEXT_Y, 1.0f, PlannhColors.titleColor("Group"), false);
            zones.add(new ClickZone(gx, 0, gx + ARROW_W, h, this::addGroup));

            final int nx = w - NOTE_BTN_RIGHT;
            GuiDraw.drawText("N", nx, TEXT_Y, 1.0f, PlannhColors.ACCENT_BLUE.getColor(), false);
            zones.add(new ClickZone(nx, 0, nx + ARROW_W, h, this::addNote));
        }

        private void shiftSlot(final int dir) {
            final SlotSet set = PlanAPI.getSlotSet();
            if (set.slots.size() <= 1) return;
            set.activeSlot = (set.activeSlot + dir + set.slots.size()) % set.slots.size();
            canvas.setGraph(set.getActiveGraph());
            PlanAPI.save();
        }

        private void addSlot() {
            final SlotSet set = PlanAPI.getSlotSet();
            final int n = set.slots.size() + 1;
            final SlotSet.Slot slot = new SlotSet.Slot("Slot " + n, new Graph());
            set.slots.add(slot);
            set.activeSlot = set.slots.size() - 1;
            canvas.setGraph(slot.graph);
            PlanAPI.save();
        }

        private void deleteSlot() {
            final SlotSet set = PlanAPI.getSlotSet();
            if (set.slots.size() <= 1) return;
            set.slots.remove(set.activeSlot);
            if (set.activeSlot >= set.slots.size()) set.activeSlot = set.slots.size() - 1;
            canvas.setGraph(set.getActiveGraph());
            PlanAPI.save();
        }

        private void addNote() {
            int cx = -Math.round(canvas.getPanX() / canvas.getZoom());
            int cy = -Math.round((canvas.getPanY() - 60) / canvas.getZoom());
            if (cx < 0) cx = 0;
            if (cy < 0) cy = 0;
            canvas.addNote(cx, cy);
        }

        private void addGroup() {
            int cx = -Math.round(canvas.getPanX() / canvas.getZoom());
            int cy = -Math.round((canvas.getPanY() - 60) / canvas.getZoom());
            if (cx < 0) cx = 0;
            if (cy < 0) cy = 0;
            canvas.addGroup(cx, cy);
        }

        private void cycleBalanceMode() {
            final Graph g = canvas.getGraph();
            final BalanceMode next = switch (g.getBalanceMode()) {
                case NONE -> BalanceMode.FORWARD;
                case FORWARD -> BalanceMode.BACKWARD;
                case BACKWARD -> BalanceMode.NONE;
            };
            g.setBalanceMode(next);
            PlanAPI.save();
        }

        @Override
        public @Nonnull Result onMousePressed(final int mouseButton) {
            if (mouseButton != 0) return Result.IGNORE;
            final int mx = getContext().getMouseX();
            final int my = getContext().getMouseY();
            for (final ClickZone zone : zones) {
                if (zone.contains(mx, my)) {
                    zone.action.run();
                    return Result.SUCCESS;
                }
            }
            return Result.IGNORE;
        }
    }

    private static class SummaryWidget extends Widget<SummaryWidget> implements Interactable {

        private static final int WIDTH = 200;
        private static final int TITLE_H = 18;
        private static final int COLLAPSE_W = 20;
        private static final int SECTION_H = 14;
        private static final int LINE_H = 11;
        private static final int TITLE_TEXT_X = 4;
        private static final int TITLE_TEXT_Y = 3;
        private static final int COLLAPSE_TEXT_Y = 4;
        private static final int SECTION_LY_OFFSET = 4;
        private static final int SECTION_HEADER_X = 2;
        private static final int SECTION_HEADER_TEXT_X = 6;
        private static final int SECTION_HEADER_TEXT_Y_OFF = 1;
        private static final int ITEM_TEXT_X = 10;
        private static final int SECTION_END_PAD = 4;
        private static final int SEPARATOR_Y_OFF = 2;
        private static final int TOTALS_TEXT_X = 6;
        private static final int TOTALS_LINE_H = 14;
        private static final int MODE_TEXT_X = 6;
        private static final int MODE_LINE_H = 12;
        private static final int HELP_SEP_Y_OFF = 4;
        private static final int HELP_SEP_GAP = 10;
        private static final int ZOOM_TEXT_X = 6;
        private static final int ZOOM_LINE_H = 14;
        private static final int HELP_LINE_H = 10;

        private final CanvasWidget canvas;

        private int floatX;
        private int floatY;
        private boolean collapsed;

        private boolean dragging = false;
        private int dragAbsMX, dragAbsMY;
        private int dragStartX, dragStartY;

        private Graph graph() {
            return canvas.getGraph();
        }

        private SummaryMode summaryMode() {
            return PlanAPI.getSlotSet().summaryMode;
        }

        private float summaryCycleSecs(final BalanceResult br) {
            return br.totalDurationTicks() > 0 ? (float) br.totalDurationTicks() / GuiHelper.TICKS_PER_SECOND : 1f;
        }

        SummaryWidget(final CanvasWidget canvas) {
            this.canvas = canvas;
            final var set = PlanAPI.getSlotSet();
            this.floatX = set.summaryX;
            this.floatY = set.summaryY;
            this.collapsed = set.summaryCollapsed;
            pos(floatX, floatY);
            size(WIDTH, computeHeight());
        }

        private int computeHeight() {
            if (collapsed) return TITLE_H;
            final Graph g = graph();
            final BalanceResult br = g.balance();
            final Summary s = Summary.compute(br, g);
            int h = TITLE_H + SECTION_LY_OFFSET;

            if (!s.outputs()
                .isEmpty()) {
                h += SECTION_H + s.outputs()
                    .size() * LINE_H + SECTION_END_PAD;
            }
            if (!s.inputs()
                .isEmpty()) {
                h += SECTION_H + s.inputs()
                    .size() * LINE_H + SECTION_END_PAD;
            }
            if (!s.properties()
                .isEmpty()) {
                h += SECTION_H + s.properties()
                    .size() * LINE_H + SECTION_END_PAD;
            }
            if (br.totalOperations() > 0) {
                h += SECTION_H + g.getNodes()
                    .size() * LINE_H + SECTION_END_PAD;
            }
            if (br.totalDurationTicks() > 0) {
                h += SECTION_H;
            }
            h += SECTION_LY_OFFSET + TOTALS_LINE_H + 1 + HELP_LINE_H;
            h += MODE_LINE_H + HELP_LINE_H * 5;
            return h;
        }

        @Override
        public void draw(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
            final Area a = getArea();
            final int w = a.width;

            GuiDraw.drawRect(0, 0, w, a.height, PlannhColors.SUMMARY_BG.getColor());
            GuiDraw.drawRect(0, 0, w, TITLE_H, PlannhColors.SUMMARY_TITLE_BG.getColor());
            GuiDraw.drawRect(0, TITLE_H, w, 1, PlannhColors.SUMMARY_TITLE_LINE.getColor());
            GuiDraw.drawText("Summary", TITLE_TEXT_X, TITLE_TEXT_Y, 1.0f, PlannhColors.TEXT_WHITE.getColor(), false);
            GuiDraw.drawText(
                collapsed ? "[+]" : "\u2212",
                w - COLLAPSE_W,
                COLLAPSE_TEXT_Y,
                1.0f,
                PlannhColors.TEXT_MUTED.getColor(),
                false);
            if (collapsed) return;

            final Graph g = graph();
            final BalanceResult br = g.balance();
            final Summary s = Summary.compute(br, g);
            final SummaryMode sMode = summaryMode();
            final float cycleSecs = summaryCycleSecs(br);
            final boolean isCycle = sMode == SummaryMode.CYCLES;
            int ly = TITLE_H + SECTION_LY_OFFSET;

            ly = drawSection(
                ly,
                w,
                "Products",
                s.outputs(),
                PlannhColors.SECTION_PRODUCT.getColor(),
                PlannhColors.ACCENT_AMBER.getColor(),
                PlannhColors.ACCENT_AMBER.getColor(),
                cycleSecs,
                isCycle);

            ly = drawSection(
                ly,
                w,
                "External Inputs",
                s.inputs(),
                PlannhColors.SECTION_INPUT.getColor(),
                PlannhColors.ACCENT_GREEN2.getColor(),
                PlannhColors.TEXT_MUTED.getColor(),
                cycleSecs,
                isCycle);

            if (!s.properties()
                .isEmpty()) {

                ly = drawSection(
                    ly,
                    w,
                    "Properties",
                    s.properties(),
                    PlannhColors.SECTION_OPS.getColor(),
                    PlannhColors.SECTION_OPS.getColor(),
                    PlannhColors.ACCENT_BLUE.getColor(),
                    cycleSecs,
                    isCycle);
            }

            if (br.totalOperations() > 0) {
                GuiDraw.drawRect(
                    SECTION_HEADER_X,
                    ly,
                    w - SECTION_HEADER_X * 2,
                    SECTION_H,
                    PlannhColors.SECTION_OPS.getColor());
                GuiDraw.drawText(
                    "Operations",
                    SECTION_HEADER_TEXT_X,
                    ly + SECTION_HEADER_TEXT_Y_OFF,
                    1.0f,
                    PlannhColors.ACCENT_BLUE.getColor(),
                    false);
                ly += SECTION_H;
                for (final Node node : g.getNodes()) {
                    final NodeBalance nb = br.nodeBalances()
                        .get(node.id);
                    if (nb == null || nb.operations <= 0) continue;
                    GuiDraw.drawText(
                        "\u00d7" + nb.operations + "  " + node.machineName,
                        ITEM_TEXT_X,
                        ly,
                        0.8f,
                        PlannhColors.TEXT_LIGHT.getColor(),
                        false);
                    ly += LINE_H;
                }
                ly += SECTION_END_PAD;
            }

            if (br.totalOperations() > 0 || br.totalDurationTicks() > 0) {
                final StringBuilder totals = new StringBuilder();
                if (br.totalOperations() > 0) totals.append("Ops: ")
                    .append(br.totalOperations());
                if (br.totalDurationTicks() > 0) {
                    final float sec = (float) br.totalDurationTicks() / GuiHelper.TICKS_PER_SECOND;
                    if (!totals.isEmpty()) totals.append("  ");
                    if (isCycle) {
                        totals.append("Time: ")
                            .append(br.totalDurationTicks())
                            .append("t");
                        if (sec > 0) totals.append(" (")
                            .append(String.format("%.1f", sec))
                            .append("s/cycle)");
                    } else {
                        totals.append("Cycle: ")
                            .append(String.format("%.1f", sec))
                            .append("s");
                    }
                }
                GuiDraw.drawRect(0, ly - SEPARATOR_Y_OFF, w, 1, PlannhColors.SEPARATOR_LIGHT.getColor());
                GuiDraw
                    .drawText(totals.toString(), TOTALS_TEXT_X, ly, 0.9f, PlannhColors.ACCENT_BLUE.getColor(), false);
                ly += TOTALS_LINE_H;
            }

            final BalanceMode mode = g.getBalanceMode();
            final String modeStr = switch (mode) {
                case NONE -> "Mode: Normal";
                case FORWARD -> "Mode: Inputs\u2192Outputs";
                case BACKWARD -> "Mode: Outputs\u2192Inputs";
            };
            GuiDraw.drawText(modeStr, MODE_TEXT_X, ly, 0.9f, PlannhColors.ACCENT_BLUE.getColor(), false);
            ly += MODE_LINE_H;

            GuiDraw.drawRect(
                SECTION_HEADER_X,
                ly + HELP_SEP_Y_OFF,
                w - SECTION_HEADER_X * 2,
                1,
                PlannhColors.SEPARATOR_DIM.getColor());
            ly += HELP_SEP_GAP;
            GuiDraw.drawText(
                "Zoom: " + canvas.getZoomPercent() + "%",
                ZOOM_TEXT_X,
                ly,
                0.9f,
                PlannhColors.TEXT_MUTED.getColor(),
                false);
            ly += ZOOM_LINE_H;
            GuiDraw.drawText("[Scroll] zoom", 6, ly, 0.8f, PlannhColors.TEXT_FAINT.getColor(), false);
            ly += HELP_LINE_H;
            GuiDraw.drawText("[MMB] pan", 6, ly, 0.8f, PlannhColors.TEXT_FAINT.getColor(), false);
            ly += HELP_LINE_H;
            GuiDraw.drawText("[LMB drag] move node", 6, ly, 0.8f, PlannhColors.TEXT_FAINT.getColor(), false);
            ly += HELP_LINE_H;
            GuiDraw.drawText("[Double-click] open NEI", 6, ly, 0.8f, PlannhColors.TEXT_FAINT.getColor(), false);
            ly += HELP_LINE_H;
            GuiDraw.drawText("[+ in NEI GUI] add recipe", 6, ly, 0.8f, PlannhColors.TEXT_FAINT.getColor(), false);
        }

        private int drawSection(int ly, final int w, final String title, final List<Summary.Line<?>> items,
            final int headerColor, final int titleColor, final int itemColor, final float cycleSecs,
            final boolean isCycle) {
            if (items.isEmpty()) return ly;
            GuiDraw.drawRect(SECTION_HEADER_X, ly, w - SECTION_HEADER_X * 2, SECTION_H, headerColor);
            GuiDraw.drawText(
                title + " (" + items.size() + ")",
                SECTION_HEADER_TEXT_X,
                ly + SECTION_HEADER_TEXT_Y_OFF,
                1.0f,
                titleColor,
                false);
            ly += SECTION_H;
            for (final var item : items) {
                final String text = item.displayAmount(isCycle ? item.amount() : item.amount() / cycleSecs)
                    + (isCycle ? " x " : "/s ")
                    + item.displayName();

                GuiDraw.drawText(text, ITEM_TEXT_X, ly, 0.8f, itemColor, false);
                ly += LINE_H;
            }
            return ly + SECTION_END_PAD;
        }

        @Override
        public @Nonnull Result onMousePressed(final int mouseButton) {
            if (mouseButton != 0) return Result.IGNORE;
            final int mx = getContext().getMouseX();
            final int my = getContext().getMouseY();

            if (my < TITLE_H && mx >= WIDTH - COLLAPSE_W) {
                collapsed = !collapsed;
                PlanAPI.getSlotSet().summaryCollapsed = collapsed;
                PlanAPI.save();
                size(WIDTH, computeHeight());
                return Result.SUCCESS;
            }

            if (my < TITLE_H) {
                dragging = true;
                dragAbsMX = getContext().getAbsMouseX();
                dragAbsMY = getContext().getAbsMouseY();
                dragStartX = floatX;
                dragStartY = floatY;
                return Result.SUCCESS;
            }

            return Result.IGNORE;
        }

        @Override
        public boolean onMouseRelease(final int mouseButton) {
            if (dragging) {
                final var set = PlanAPI.getSlotSet();
                set.summaryX = floatX;
                set.summaryY = floatY;
                PlanAPI.save();
            }
            dragging = false;
            return true;
        }

        @Override
        public void onMouseDrag(final int mouseButton, final long timeSinceClick) {
            if (!dragging) return;
            floatX = dragStartX + (getContext().getAbsMouseX() - dragAbsMX);
            floatY = dragStartY + (getContext().getAbsMouseY() - dragAbsMY);
            pos(floatX, floatY);
        }
    }
}
