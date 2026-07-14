package com.sbancuz.plannh.gui;

import java.util.List;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.data.flowchart.Node;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.EnumValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.menu.Menu;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.Balancer.NodeBalance;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.data.flowchart.Summary.SummaryMode;

import codechicken.nei.LayoutManager;
import gregtech.common.gui.modularui.widget.EnumCycleButtonWidget;
import lombok.Getter;

public class FlowchartScreen extends ModularScreen {

    private static final int LEFT_MARGIN = 5;
    private static final int RIGHT_MARGIN = 15;
    private static final int TOP_MARGIN = 30;
    private static final int BOTTOM_MARGIN = 30;

    @Getter
    private final CanvasWidget canvas;

    private FlowchartScreen(final ModularPanel panel, CanvasWidget canvas) {
        super(PlanNH.MODID, panel);
        getContext().setSettings(new UISettings());
        getContext().getUISettings()
            .getRecipeViewerSettings()
            .enable();

        this.canvas = canvas;
    }

    public static FlowchartScreen create() {
        final ModularPanel panel = ModularPanel.defaultPanel("flowchart_main")
            .fullScreenInvisible()
            .left(LEFT_MARGIN)
            .marginBottom(BOTTOM_MARGIN)
            .marginTop(TOP_MARGIN)
            .widthRelOffset(
                () -> (double) (LayoutManager.itemPanel.x - RIGHT_MARGIN)
                    / Minecraft.getMinecraft().currentScreen.width,
                0);

        final Flow mainColumn = Flow.column()
            .full();

        Menu<?> contextMenu = new Menu<>();

        final CanvasWidget canvas = new CanvasWidget(contextMenu, panel);

        contextMenu.setEnabledIf(_ -> canvas.isMenuOpen())
            .coverChildren()
            .background()
            .relativeToScreen()
            .child(
                new ListWidget<>().coverChildrenHeight()
                    .width(100)
                    .child(new ButtonWidget<>().onMousePressed(_ -> {
                        canvas.addNote(canvas.getCanvasMouseX(), canvas.getCanvasMouseY());
                        return true;
                    })
                        .fullWidth()
                        .background(
                            new Rectangle().color(PlannhColors.CONTEXT_BG.getColor()),
                            new Rectangle().hollow()
                                .color(PlannhColors.CONTEXT_BORDER.getColor()))
                        .overlay(IKey.str("Add Note")))
                    .child(new ButtonWidget<>().onMousePressed(_ -> {
                        canvas.addGroup(canvas.getCanvasMouseX(), canvas.getCanvasMouseY());
                        return true;
                    })
                        .fullWidth()
                        .background(
                            new Rectangle().color(PlannhColors.CONTEXT_BG.getColor()),
                            new Rectangle().hollow()
                                .color(PlannhColors.CONTEXT_BORDER.getColor()))
                        .overlay(IKey.str("Add Group"))));

        mainColumn.child(
            Flow.row()
                .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                .coverChildrenHeight()
                .fullWidth()
                .child(
                    Flow.row()
                        .coverChildren()
                        .childPadding(2)
                        .child(new ButtonWidget<>().onMousePressed(_ -> {
                            cycleGraphs(canvas, -1);
                            return true;
                        })
                            .overlay(IKey.str("<"))
                            .addTooltipLine("Previous Graph"))
                        .child(
                            new TextFieldWidget().value(
                                new StringValue.Dynamic(
                                    () -> Plan.getActiveGraph()
                                        .getName(),
                                    val -> Plan.getActiveGraph()
                                        .setName(val)))
                                .background()
                                .hoverBackground())
                        .child(new ButtonWidget<>().onMousePressed(_ -> {
                            cycleGraphs(canvas, 1);
                            return true;
                        })
                            .overlay(IKey.str(">"))
                            .addTooltipLine("Next Graph"))
                        .child(new ButtonWidget<>().onMousePressed(_ -> {
                            addGraph(canvas);
                            return true;
                        })
                            .overlay(
                                IKey.str("+")
                                    .color(Color.GREEN.main))
                            .addTooltipLine("Add Graph"))
                        .child(new ButtonWidget<>().onMousePressed(_ -> {
                            deleteGraph(canvas);
                            return true;
                        })
                            .overlay(
                                IKey.str("x")
                                    .color(Color.RED.main))
                            .addTooltipLine("Remove Graph")))
                .child(
                    Flow.row()
                        .coverChildren()
                        .childPadding(2)
                        .child(
                            new ToggleButton().value(
                                new BoolValue.Dynamic(
                                    () -> Plan.getInstance()
                                        .isSnapToGrid(),
                                    val -> Plan.getInstance()
                                        .setSnapToGrid(val)))
                                .overlay(IKey.str("S2G"))
                                .addTooltipLine("Snap to Grid"))
                        .child(
                            new EnumCycleButtonWidget<>(BalanceMode.class)
                                .value(
                                    new EnumValue.Dynamic<>(
                                        BalanceMode.class,
                                        () -> Plan.getActiveGraph()
                                            .getBalanceMode(),
                                        val -> Plan.getActiveGraph()
                                            .setBalanceMode(val)))
                                .overlay(val -> IKey.str("M:" + switch (val) {
                                case NONE -> "-";
                                case FORWARD -> "F";
                                case BACKWARD -> "B";
                                }))
                                .addTooltipLine("Cycle Balance Modes"))
                        .child(
                            new EnumCycleButtonWidget<>(SummaryMode.class)
                                .value(
                                    new EnumValue.Dynamic<>(
                                        SummaryMode.class,
                                        () -> Plan.getInstance()
                                            .getSummaryMode(),
                                        val -> Plan.getInstance()
                                            .setSummaryMode(val)))
                                .overlay(val -> IKey.str("S:" + (val == SummaryMode.CYCLES ? "C" : "T")))
                                .addTooltipLine("Cycle Summary Modes"))
                        .child(new ButtonWidget<>().onMousePressed(_ -> {
                            canvas.addGroup(canvas.getCanvasScreenCenterX(), canvas.getCanvasScreenCenterY());
                            return true;
                        })
                            .overlay(IKey.str("G"))
                            .addTooltipLine("Add Group"))
                        .child(new ButtonWidget<>().onMousePressed(_ -> {
                            canvas.addNote(canvas.getCanvasScreenCenterX(), canvas.getCanvasScreenCenterY());
                            return true;
                        })
                            .overlay(IKey.str("N"))
                            .addTooltipLine("Add Note"))
                        .child(new ButtonWidget<>().onMousePressed(_ -> {
                            PlanAPI.shareGraph(canvas.getGraph());
                            return true;
                        })
                            .overlay(
                                IKey.str("Sh")
                                    .color(Color.GREEN_ACCENT.main))
                            .addTooltipLine("Share Graph"))
                        .child(new ButtonWidget<>().onMousePressed(_ -> {
                            PlanAPI.copyToClipboard(canvas.getGraph());
                            Minecraft.getMinecraft().thePlayer.addChatMessage(
                                new ChatComponentText(
                                    "[" + PlanNH.MODID
                                        + "] "
                                        + StatCollector.translateToLocal("plannh.share.copy_to_clipboard")));
                            return true;
                        })
                            .overlay(
                                IKey.str("Cp")
                                    .color(Color.BLUE_ACCENT.main))
                            .addTooltipLine("Copy Graph"))
                        .child(new ButtonWidget<>().onMousePressed(_ -> {
                            final Graph graph = PlanAPI.importFromClipboard();
                            if (graph == null) return true;
                            PlanAPI.importGraph(graph);
                            canvas.setGraph(graph);
                            Minecraft.getMinecraft().thePlayer.addChatMessage(
                                new ChatComponentText(
                                    "[" + PlanNH.MODID
                                        + "] "
                                        + StatCollector.translateToLocal("plannh.share.copy_from_clipboard")));
                            return true;
                        })
                            .overlay(
                                IKey.str("Im")
                                    .color(Color.YELLOW_ACCENT.main))
                            .addTooltipLine("Import Graph"))))
            .child(canvas);

        panel.child(mainColumn);
        panel.child(new SummaryWidget(canvas));
        panel.child(contextMenu);

        return new FlowchartScreen(panel, canvas);
    }

    @Override
    public void onClose() {
        PlanAPI.save();
        super.onClose();
    }

    private static void refreshGraph(CanvasWidget canvas) {
        canvas.setGraph(Plan.getActiveGraph());
    }

    private static void cycleGraphs(CanvasWidget canvas, final int dir) {
        final Plan plan = Plan.getInstance();
        final int size = plan.getGraphs()
            .size();
        if (size <= 1) return;
        plan.setActiveIndex((plan.getActiveIndex() + dir + size) % size);
        refreshGraph(canvas);
    }

    private static void addGraph(CanvasWidget canvas) {
        final Plan plan = Plan.getInstance();
        final int size = plan.getGraphs()
            .size();
        plan.getGraphs()
            .add(new Graph("Slot " + (size + 1)));
        plan.setActiveIndex(size);
        refreshGraph(canvas);
    }

    private static void deleteGraph(CanvasWidget canvas) {
        final Plan plan = Plan.getInstance();
        final int size = plan.getGraphs()
            .size();
        if (size <= 1) return;
        final int active = plan.getActiveIndex();
        plan.getGraphs()
            .remove(active);
        if (active >= size - 1) plan.setActiveIndex(size - 2);
        refreshGraph(canvas);
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
            return Plan.getInstance()
                .getSummaryMode();
        }

        private float summaryCycleSecs(final BalanceResult br) {
            return br.totalDurationTicks() > 0 ? (float) br.totalDurationTicks() / GuiHelper.TICKS_PER_SECOND : 1f;
        }

        SummaryWidget(final CanvasWidget canvas) {
            this.canvas = canvas;
            final Plan plan = Plan.getInstance();
            this.floatX = plan.getSummaryX();
            this.floatY = plan.getSummaryY();
            this.collapsed = plan.isSummaryCollapsed();
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
                for (final Node node : g.getNodes()
                    .values()) {
                    final NodeBalance nb = br.nodeBalances()
                        .get(node.getId());
                    if (nb == null || nb.operations <= 0) continue;
                    GuiDraw.drawText(
                        "\u00d7" + nb.operations + "  " + node.getMachineName(),
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
                "Zoom: " + canvas.getGraph()
                    .getZoom() * 100 + "%",
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
                Plan.getInstance()
                    .setSummaryCollapsed(collapsed);
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
                final var set = Plan.getInstance();
                set.setSummaryX(floatX);
                set.setSummaryY(floatY);
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
