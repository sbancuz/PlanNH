package com.sbancuz.plannh.gui;

import static codechicken.lib.gui.GuiDraw.drawMultilineTip;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

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
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widget.sizer.Unit;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.menu.Menu;
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
import com.sbancuz.plannh.gui.components.CycleButton;

import codechicken.nei.LayoutManager;
import codechicken.nei.guihook.GuiContainerManager;

public class FlowchartScreen extends ModularScreen {

    private static final int LEFT_MARGIN = 5;
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

    private static double panelRight() {
        return Minecraft.getMinecraft().currentScreen.width - LayoutManager.itemPanel.x + 8;
    }

    public static FlowchartScreen create() {
        final Graph graph = PlanAPI.getActiveGraph();

        final ModularPanel panel = ModularPanel.defaultPanel("flowchart_main")
            .fullScreenInvisible()
            .marginLeft(LEFT_MARGIN)
            .marginBottom(BOTTOM_MARGIN)
            .marginTop(TOP_MARGIN)
            .widthRel(0.75f)
            .right(FlowchartScreen::panelRight, Unit.Measure.PIXEL);

        final Flow mainColumn = Flow.column()
            .full();

        Menu<?> contextMenu = new Menu<>();
        final CanvasWidget canvas = new CanvasWidget(graph, contextMenu);

        contextMenu.setEnabledIf(_ -> canvas.isMenuOpen())
            .coverChildren()
            .background()
            .relativeToScreen()
            .child(
                new ListWidget<>().coverChildrenHeight()
                    .width(100)
                    .child(new ButtonWidget<>().onMousePressed(_ -> {
                        canvas.addNote();
                        return true;
                    })
                        .fullWidth()
                        .background(
                            new Rectangle().color(PlannhColors.CONTEXT_BG.getColor()),
                            new Rectangle().hollow()
                                .color(PlannhColors.CONTEXT_BORDER.getColor()))
                        .overlay(
                            IKey.str("Add Note")
                                .color(Color.WHITE.main)))
                    .child(new ButtonWidget<>().onMousePressed(_ -> {
                        canvas.addGroup();
                        return true;
                    })
                        .fullWidth()
                        .background(
                            new Rectangle().color(PlannhColors.CONTEXT_BG.getColor()),
                            new Rectangle().hollow()
                                .color(PlannhColors.CONTEXT_BORDER.getColor()))
                        .overlay(
                            IKey.str("Add Group")
                                .color(Color.WHITE.main))));

        final SlotSet set = PlanAPI.getSlotSet();

        mainColumn.child(
            Flow.row()
                .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                .height(16)
                .fullWidth()
                .child(
                    Flow.row()
                        .coverChildren()
                        .childPadding(2)
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("<"))
                                .onMousePressed(_ -> {
                                    shiftSlot(canvas, -1);
                                    return true;
                                }))
                        .child(
                            IKey.str("Slot " + (PlanAPI.getSlotSet().activeSlot + 1))
                                .asWidget()
                                .color(Color.WHITE.main))
                        .child(
                            new ButtonWidget<>().overlay(IKey.str(">"))
                                .onMousePressed(_ -> {
                                    shiftSlot(canvas, 1);
                                    return true;
                                }))
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("+"))
                                .onMousePressed(_ -> {
                                    addSlot(canvas);
                                    return true;
                                }))
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("\u00d7"))
                                .onMousePressed(_ -> {
                                    deleteSlot(canvas);
                                    return true;
                                })))
                .child(
                    Flow.row()
                        .coverChildren()
                        .childPadding(2)
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("S2G"))
                                .onMousePressed(_ -> {
                                    final Graph g = canvas.getGraph();
                                    g.setSnapToGrid(!g.isSnapToGrid());
                                    PlanAPI.save();
                                    return true;
                                }))
                        .child(
                            new CycleButton<>(BalanceMode.class).overlay(v -> IKey.str(CycleButton.shortName(v)))
                                .current(
                                    canvas.getGraph()
                                        .getBalanceMode())
                                .onCycle(next -> {
                                    canvas.getGraph()
                                        .setBalanceMode(next);
                                    PlanAPI.save();
                                }))
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("Ops"))
                                .onMousePressed(_ -> {
                                    final Graph g = canvas.getGraph();
                                    if (g.getBalanceMode() != BalanceMode.NONE) {
                                        g.setOpsMode(!g.isOpsMode());
                                        PlanAPI.save();
                                    }
                                    return true;
                                }))
                        .child(
                            new CycleButton<>(SummaryMode.class).overlay(v -> IKey.str(CycleButton.shortName(v)))
                                .current(set.summaryMode)
                                .onCycle(next -> {
                                    set.summaryMode = next;
                                    PlanAPI.save();
                                }))
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("G"))
                                .onMousePressed(_ -> {
                                    canvas.addGroup();
                                    return true;
                                }))
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("N"))
                                .onMousePressed(_ -> {
                                    canvas.addNote();
                                    return true;
                                }))
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("Sh"))
                                .onMousePressed(_ -> {
                                    PlanAPI.shareGraph(canvas.getGraph());
                                    return true;
                                }))
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("Cp"))
                                .onMousePressed(_ -> {
                                    copyGraph(canvas);
                                    return true;
                                }))
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("Im"))
                                .onMousePressed(_ -> {
                                    importGraph(canvas);
                                    return true;
                                }))))
            .child(canvas);

        panel.child(mainColumn);
        panel.child(new SummaryWidget(canvas));
        panel.child(contextMenu);

        return new FlowchartScreen(panel, graph, canvas);
    }

    @Override
    public void onClose() {
        PlanAPI.save();
        super.onClose();
    }

    @Override
    public void drawForeground() {
        super.drawForeground();
        drawHoveredIngredientTooltip();
    }

    /**
     * Mouse-anchored tooltip (full NEI item tooltip, including handler lines) for the hovered
     * ingredient: recipe-grid stacks, throughput rows, and port pins. Drawn in the foreground
     * phase - the last thing rendered, depth off - so it can never be buried by nodes. NEI's own
     * tooltip pass is suppressed on MUI screens while a widget is hovered, so this is the only
     * copy.
     */
    private void drawHoveredIngredientTooltip() {
        // stackUnderMouse, not getStackForRecipeViewer: the NEI entry point also arms the
        // pending-lookup origin, and a per-frame tooltip must not mutate lookup state.
        if (!(getContext().getHovered() instanceof final RecipeNodeWidget nodeWidget)) return;
        final ItemStack stack = nodeWidget.stackUnderMouse();
        if (stack == null) return;
        final List<String> lines = GuiContainerManager.itemDisplayNameMultiline(stack, null, true);
        if (lines.isEmpty()) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        drawMultilineTip(getContext().getAbsMouseX() + 12, getContext().getAbsMouseY() - 12, lines);
        GL11.glPopAttrib();
    }

    // ── Slot bar helpers ──

    private static void shiftSlot(final CanvasWidget canvas, final int dir) {
        final SlotSet set = PlanAPI.getSlotSet();
        if (set.slots.size() <= 1) return;
        set.activeSlot = (set.activeSlot + dir + set.slots.size()) % set.slots.size();
        canvas.setGraph(set.getActiveGraph());
        PlanAPI.save();
    }

    private static void addSlot(final CanvasWidget canvas) {
        final SlotSet set = PlanAPI.getSlotSet();
        final int n = set.slots.size() + 1;
        final SlotSet.Slot slot = new SlotSet.Slot("Slot " + n, new Graph());
        set.slots.add(slot);
        set.activeSlot = set.slots.size() - 1;
        canvas.setGraph(slot.graph);
        PlanAPI.save();
    }

    private static void deleteSlot(final CanvasWidget canvas) {
        final SlotSet set = PlanAPI.getSlotSet();
        if (set.slots.size() <= 1) return;
        set.slots.remove(set.activeSlot);
        if (set.activeSlot >= set.slots.size()) set.activeSlot = set.slots.size() - 1;
        canvas.setGraph(set.getActiveGraph());
        PlanAPI.save();
    }

    private static void copyGraph(final CanvasWidget canvas) {
        PlanAPI.copyToClipboard(canvas.getGraph());
        Minecraft.getMinecraft().thePlayer.addChatMessage(
            new ChatComponentText(
                "[" + PlanNH.MODID + "] " + StatCollector.translateToLocal("plannh.share.copy_to_clipboard")));
    }

    private static void importGraph(final CanvasWidget canvas) {
        final Graph g = PlanAPI.importFromClipboard();
        if (g == null) return;
        PlanAPI.importGraph(g);
        canvas.setGraph(g);
        Minecraft.getMinecraft().thePlayer.addChatMessage(
            new ChatComponentText(
                "[" + PlanNH.MODID + "] " + StatCollector.translateToLocal("plannh.share.copy_from_clipboard")));
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
            size(WIDTH, 200);
        }

        private int computeHeight(final Summary summary, final BalanceResult br) {
            if (collapsed) return TITLE_H;
            final Graph g = graph();
            int h = TITLE_H + SECTION_LY_OFFSET;

            if (!summary.outputs()
                .isEmpty()) {
                h += SECTION_H + summary.outputs()
                    .size() * LINE_H + SECTION_END_PAD;
            }
            if (!summary.inputs()
                .isEmpty()) {
                h += SECTION_H + summary.inputs()
                    .size() * LINE_H + SECTION_END_PAD;
            }
            if (!summary.properties()
                .isEmpty()) {
                h += SECTION_H + summary.properties()
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
            final Summary s = g.summary();
            final BalanceResult br = g.balance();
            size(WIDTH, computeHeight(s, br));
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
                    if (nb == null || nb.operations() <= 0) continue;
                    GuiDraw.drawText(
                        "\u00d7" + nb.operations() + "  " + node.machineName,
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
            final String modeStr = String.format(
                StatCollector.translateToLocal("plannh.gui.balancer_mode"),
                String.format(mode.displayName(), g.isOpsMode() ? ", ops" : ""));
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
                "Zoom: " + Math.round(
                    canvas.getGraph()
                        .getZoom() * 100)
                    + "%",
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
                final Graph g = graph();
                final Summary s = g.summary();
                size(WIDTH, computeHeight(s, g.balance()));
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
