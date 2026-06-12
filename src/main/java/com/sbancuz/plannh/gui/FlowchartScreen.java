package com.sbancuz.plannh.gui;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.Balancer.NodeBalance;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.SlotSet;
import com.sbancuz.plannh.data.flowchart.Summary;

public class FlowchartScreen extends ModularScreen {

    public final Graph graph;
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

        final ModularPanel panel = ModularPanel.defaultPanel("flowchart_main");
        panel.fullScreenInvisible();

        panel.child(
            new SlotBarWidget(canvas).left(0)
                .top(18)
                .right(176)
                .height(22));

        panel.child(
            canvas.left(0)
                .top(42)
                .right(176)
                .bottom(20));

        panel.addChild(new SummaryWidget(canvas), -1);

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

            final int y = 0;

            final int px = 4;
            GuiDraw.drawText("<", px, 4, 1.0f, PlannhColors.ACCENT_BLUE.getColor(), false);
            zones.add(new ClickZone(px, y, px + 14, y + h, () -> shiftSlot(-1)));
            final int nameX = px + 16;
            GuiDraw.drawText(name, nameX, 4, 1.0f, PlannhColors.TEXT_WHITE.getColor(), false);
            final int nameW = name.length() * 6 + 8;
            final int nbx = nameX + nameW;
            GuiDraw.drawText(">", nbx, 4, 1.0f, PlannhColors.ACCENT_BLUE.getColor(), false);
            zones.add(new ClickZone(nbx, y, nbx + 14, y + h, () -> shiftSlot(1)));

            final int ax = w - 30;
            GuiDraw.drawText("+", ax, 4, 1.0f, PlannhColors.ACCENT_GREEN.getColor(), false);
            zones.add(new ClickZone(ax, y, ax + 14, y + h, this::addSlot));

            final int dx = w - 15;
            GuiDraw.drawText("\u00d7", dx, 4, 1.0f, PlannhColors.ACCENT_RED.getColor(), false);
            zones.add(new ClickZone(dx, y, dx + 14, y + h, this::deleteSlot));

            final int nx = w - 44;
            GuiDraw.drawText("N", nx, 4, 1.0f, PlannhColors.ACCENT_BLUE.getColor(), false);
            zones.add(new ClickZone(nx, y, nx + 14, y + h, this::addNote));

            final int gx = w - 58;
            GuiDraw.drawText("G", gx, 4, 1.0f, PlannhColors.titleColor("Group"), false);
            zones.add(new ClickZone(gx, y, gx + 14, y + h, this::addGroup));
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

        @Override
        public @NotNull Result onMousePressed(final int mouseButton) {
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
            int h = TITLE_H + 4;

            if (!br.netOutputs()
                .isEmpty()) {
                h += 14 + br.netOutputs()
                    .size() * 11 + 4;
            }
            if (!br.netInputs()
                .isEmpty()) {
                h += 14 + br.netInputs()
                    .size() * 11 + 4;
            }
            if (!br.netFluidOutputs()
                .isEmpty()) {
                h += 14 + br.netFluidOutputs()
                    .size() * 11 + 4;
            }
            if (!br.netFluidInputs()
                .isEmpty()) {
                h += 14 + br.netFluidInputs()
                    .size() * 11 + 4;
            }
            if (br.totalOperations() > 0) {
                h += 14 + g.getNodes()
                    .size() * 11 + 4;
            }
            if (br.totalDurationTicks() > 0) {
                h += 14;
            }
            h += 4 + 1 + 10;
            h += 14 + 10 + 10 + 10 + 10 + 10;
            return h;
        }

        @Override
        public void draw(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
            final Area a = getArea();
            final int w = a.width;

            GuiDraw.drawRect(0, 0, w, a.height, PlannhColors.SUMMARY_BG.getColor());
            GuiDraw.drawRect(0, 0, w, TITLE_H, PlannhColors.SUMMARY_TITLE_BG.getColor());
            GuiDraw.drawRect(0, TITLE_H, w, 1, PlannhColors.SUMMARY_TITLE_LINE.getColor());
            GuiDraw.drawText("Summary", 4, 3, 1.0f, PlannhColors.TEXT_WHITE.getColor(), false);
            GuiDraw.drawText(
                collapsed ? "[+]" : "\u2212",
                w - COLLAPSE_W,
                4,
                1.0f,
                PlannhColors.TEXT_MUTED.getColor(),
                false);
            if (collapsed) return;

            final Graph g = graph();
            final BalanceResult br = g.balance();
            int ly = TITLE_H + 4;

            ly = drawSection(
                ly,
                w,
                "Products",
                br.netOutputs(),
                PlannhColors.SECTION_PRODUCT.getColor(),
                PlannhColors.ACCENT_AMBER.getColor(),
                PlannhColors.ACCENT_AMBER.getColor(),
                i -> ((Summary.SummaryLine) i).totalCount + "x " + ((Summary.SummaryLine) i).stack.getDisplayName());

            ly = drawSection(
                ly,
                w,
                "External Inputs",
                br.netInputs(),
                PlannhColors.SECTION_INPUT.getColor(),
                PlannhColors.ACCENT_GREEN2.getColor(),
                PlannhColors.TEXT_MUTED.getColor(),
                i -> ((Summary.SummaryLine) i).totalCount + "x " + ((Summary.SummaryLine) i).stack.getDisplayName());

            ly = drawFluidSection(
                ly,
                w,
                "Fluid Products",
                br.netFluidOutputs(),
                PlannhColors.SECTION_FLUID_OUT.getColor(),
                PlannhColors.ACCENT_CYAN2.getColor(),
                PlannhColors.ACCENT_CYAN.getColor());

            ly = drawFluidSection(
                ly,
                w,
                "Fluid Inputs",
                br.netFluidInputs(),
                PlannhColors.SECTION_FLUID_IN.getColor(),
                PlannhColors.ACCENT_BLUE2.getColor(),
                PlannhColors.ACCENT_BLUE3.getColor());

            if (br.totalOperations() > 0) {
                GuiDraw.drawRect(2, ly, w - 4, 12, PlannhColors.SECTION_OPS.getColor());
                GuiDraw.drawText("Operations", 6, ly + 1, 1.0f, PlannhColors.ACCENT_BLUE.getColor(), false);
                ly += SECTION_H;
                for (final Node node : g.getNodes()) {
                    final NodeBalance nb = br.nodeBalances()
                        .get(node.id);
                    if (nb == null || nb.operations <= 0) continue;
                    GuiDraw.drawText(
                        "\u00d7" + nb.operations + "  " + node.machineName,
                        10,
                        ly,
                        0.8f,
                        PlannhColors.TEXT_LIGHT.getColor(),
                        false);
                    ly += LINE_H;
                }
                ly += 4;
            }

            if (br.totalOperations() > 0 || br.totalDurationTicks() > 0) {
                final StringBuilder totals = new StringBuilder();
                if (br.totalOperations() > 0) totals.append("Ops: ")
                    .append(br.totalOperations());
                if (br.totalDurationTicks() > 0) {
                    if (!totals.isEmpty()) totals.append("  ");
                    final float sec = (float) br.totalDurationTicks() / GuiHelper.TICKS_PER_SECOND;
                    totals.append("Time: ")
                        .append(br.totalDurationTicks())
                        .append("t");
                    if (sec > 0) totals.append(" (")
                        .append(String.format("%.1f", sec))
                        .append("s)");
                }
                GuiDraw.drawRect(0, ly - 2, w, 1, PlannhColors.SEPARATOR_LIGHT.getColor());
                GuiDraw.drawText(totals.toString(), 6, ly, 0.9f, PlannhColors.ACCENT_BLUE.getColor(), false);
                ly += 14;
            }

            GuiDraw.drawRect(2, ly + 4, w - 4, 1, PlannhColors.SEPARATOR_DIM.getColor());
            ly += 10;
            GuiDraw.drawText(
                "Zoom: " + canvas.getZoomPercent() + "%",
                6,
                ly,
                0.9f,
                PlannhColors.TEXT_MUTED.getColor(),
                false);
            ly += 14;
            GuiDraw.drawText("[Scroll] zoom", 6, ly, 0.8f, PlannhColors.TEXT_FAINT.getColor(), false);
            ly += 10;
            GuiDraw.drawText("[MMB] pan", 6, ly, 0.8f, PlannhColors.TEXT_FAINT.getColor(), false);
            ly += 10;
            GuiDraw.drawText("[LMB drag] move node", 6, ly, 0.8f, PlannhColors.TEXT_FAINT.getColor(), false);
            ly += 10;
            GuiDraw.drawText("[Double-click] open NEI", 6, ly, 0.8f, PlannhColors.TEXT_FAINT.getColor(), false);
            ly += 10;
            GuiDraw.drawText("[+ in NEI GUI] add recipe", 6, ly, 0.8f, PlannhColors.TEXT_FAINT.getColor(), false);
        }

        private int drawSection(int ly, final int w, final String title, final List<?> items, final int headerColor,
            final int titleColor, final int itemColor, final java.util.function.Function<Object, String> labelFn) {
            if (items.isEmpty()) return ly;
            GuiDraw.drawRect(2, ly, w - 4, 12, headerColor);
            GuiDraw.drawText(title + " (" + items.size() + ")", 6, ly + 1, 1.0f, titleColor, false);
            ly += SECTION_H;
            for (final Object item : items) {
                GuiDraw.drawText(labelFn.apply(item), 10, ly, 0.8f, itemColor, false);
                ly += LINE_H;
            }
            return ly + 4;
        }

        private static String fluidLabel(final Object item) {
            final var line = (com.sbancuz.plannh.data.flowchart.Summary.FluidSummaryLine) item;
            return formatFluidAmount(line.totalAmount) + " " + line.fluid.getLocalizedName();
        }

        private int drawFluidSection(int ly, final int w, final String title,
            final List<com.sbancuz.plannh.data.flowchart.Summary.FluidSummaryLine> items, final int headerColor,
            final int titleColor, final int itemColor) {
            if (items.isEmpty()) return ly;
            GuiDraw.drawRect(2, ly, w - 4, 12, headerColor);
            GuiDraw.drawText(title + " (" + items.size() + ")", 6, ly + 1, 1.0f, titleColor, false);
            ly += SECTION_H;
            for (final var line : items) {
                GuiDraw.drawText(fluidLabel(line), 10, ly, 0.8f, itemColor, false);
                ly += LINE_H;
            }
            return ly + 4;
        }

        @Override
        public @NotNull Result onMousePressed(final int mouseButton) {
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

        private static String formatFluidAmount(final int mb) {
            if (mb >= 1000) return (mb / 1000) + "." + ((mb % 1000) / 100) + "B";
            return mb + "mB";
        }
    }
}
