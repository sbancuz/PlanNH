package com.sbancuz.plannh.gui;

import static codechicken.lib.gui.GuiDraw.drawMultilineTip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.menu.Menu;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.sbancuz.plannh.Config;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.data.flowchart.Summary.SummaryMode;
import com.sbancuz.plannh.data.flowchart.Summary.SummarySection;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceView;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer.NodeBalance;
import com.sbancuz.plannh.data.flowchart.balancer.ChoiceKey;
import com.sbancuz.plannh.data.flowchart.balancer.Note;
import com.sbancuz.plannh.data.flowchart.balancer.Severity;
import com.sbancuz.plannh.gui.components.CycleButton;
import com.sbancuz.plannh.nei.NEIPlanConfig;

import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.guihook.GuiContainerManager;
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

        CanvasWidget canvas = new CanvasWidget(contextMenu, panel);

        // Target-rate editor: one numeric field in a floating menu. numbersDouble gives the MUI2
        // math parser, so "2k" and "1/3" work; committing (enter or clicking away) closes it.
        final Menu<?> targetEditor = new Menu<>();
        // Re-read in onFocus: the field only refreshes its bound value while unfocused, so it
        // would otherwise show the previously edited port's rate.
        final DoubleValue.Dynamic targetValue = new DoubleValue.Dynamic(
            canvas::editedTargetRate,
            canvas::setEditedTargetRate);
        final TextFieldWidget targetField = new TextFieldWidget() {

            @Override
            public void onFocus(final ModularGuiContext context) {
                super.onFocus(context);
                // trimmed: String.valueOf(double) renders 26 as "26.0"
                setText(GuiHelper.trimTrailingZeros(targetValue.getStringValue()));
                handler.setCursor(0, getText().length(), true, false);
            }
        }.numbersDouble(0, 1_000_000)
            .value(targetValue)
            .size(70, 14);
        // Focus from the field's own update listener: the only place the widget is guaranteed
        // to be in the tree.
        targetField.onUpdateListener(w -> {
            if (w.isValid() && canvas.consumeTargetEditorFocus()) {
                w.getContext()
                    .focus(w);
            }
        }, true);
        targetEditor.setEnabledIf(_ -> canvas.isTargetEditorOpen())
            .coverChildren()
            .background()
            .relativeToScreen()
            .child(targetField);
        canvas.setTargetEditorMenu(targetEditor);

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
                            new ButtonWidget<>().overlay(
                                IKey.str("\u21ba")
                                    .scale(2f))
                                .onMousePressed(_ -> {
                                    canvas.undoGraph();
                                    return true;
                                }))
                        .child(
                            new ButtonWidget<>().overlay(
                                IKey.str("\u21bb")
                                    .scale(2f))
                                .onMousePressed(_ -> {
                                    canvas.redoGraph();
                                    return true;
                                }))
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("AL"))
                                .tooltipStatic(t -> t.addLine(IKey.str("Auto layout")))
                                .onMousePressed(_ -> {
                                    canvas.autoLayoutNodes();
                                    return true;
                                }))
                        .child(
                            new ToggleButton().value(
                                new BoolValue.Dynamic(
                                    () -> Plan.getInstance()
                                        .isSnapToGrid(),
                                    val -> Plan.getInstance()
                                        .setSnapToGrid(val)))
                                .overlay(IKey.str("S2G"))
                                .addTooltipLine("Snap to Grid"))
                        /*
                         * .child(
                         * new CycleButtonWidget()
                         * .value(
                         * new EnumValue.Dynamic<>(
                         * BalanceMode.class,
                         * () -> Plan.getActiveGraph()
                         * .getBalanceMode(),
                         * val -> Plan.getActiveGraph()
                         * .setBalanceMode(val)))
                         * .stateOverlay(BalanceMode.NONE, IKey.str("M:-"))
                         * .stateOverlay(BalanceMode.FORWARD, IKey.str("M:F"))
                         * .stateOverlay(BalanceMode.BACKWARD, IKey.str("M:B"))
                         * .addTooltipLine("Cycle Balance Modes"))
                         */
                        .child(
                            new CycleButton<>(BalanceMode.class).overlay(v -> IKey.str(CycleButton.shortName(v)))
                                .source(
                                    () -> canvas.getGraph()
                                        .getBalanceMode())
                                .onCycle(
                                    next -> canvas.getGraph()
                                        .setBalanceMode(next)))
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("Ops"))
                                .onMousePressed(_ -> {
                                    final Graph g = canvas.getGraph();
                                    if (g.getBalanceMode()
                                        .usesOpsMode()) {
                                        g.setOpsMode(!g.isOpsMode());
                                    }
                                    return true;
                                }))
                        /*
                         * .child(
                         * new CycleButtonWidget()
                         * .value(
                         * new EnumValue.Dynamic<>(
                         * SummaryMode.class,
                         * () -> Plan.getInstance()
                         * .getSummaryMode(),
                         * val -> Plan.getInstance()
                         * .setSummaryMode(val)))
                         * .stateOverlay(SummaryMode.CYCLES, IKey.str("S:C"))
                         * .stateOverlay(SummaryMode.THROUGHPUT, IKey.str("S:T"))
                         * .addTooltipLine("Cycle Summary Modes"))
                         */
                        .child(
                            new CycleButton<>(SummaryMode.class).overlay(v -> IKey.str(CycleButton.shortName(v)))
                                .current(
                                    Plan.getInstance()
                                        .getSummaryMode())
                                .onCycle(
                                    next -> Plan.getInstance()
                                        .setSummaryMode(next)))
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
        // panel.child(new SummaryWidget(canvas));
        panel.child(contextMenu);
        panel.child(targetEditor);

        return new FlowchartScreen(panel, canvas);
    }

    @Override
    public void onClose() {
        PlanAPI.save();
        super.onClose();
    }

    // Screen level, not canvas level: the panel only offers keys to the hovered widget, so the
    // canvas never sees them while the cursor sits on the toolbar or a text field holds focus.
    // isKeyHashDown reads the live LWJGL event, which is still the one being dispatched here.
    @Override
    public boolean onKeyPressed(final char typedChar, final int keyCode) {
        final boolean undo = NEIClientConfig.isKeyHashDown(NEIPlanConfig.ConfigUndoKey.KEY);
        if (undo || NEIClientConfig.isKeyHashDown(NEIPlanConfig.ConfigRedoKey.KEY)
            || NEIClientConfig.isKeyHashDown(NEIPlanConfig.ConfigRedoAltKey.KEY)) {
            if (undo) canvas.undoGraph();
            else canvas.redoGraph();
            return true;
        }
        return super.onKeyPressed(typedChar, keyCode);
    }

    @Override
    public void drawForeground() {
        super.drawForeground();
        drawHoveredIngredientTooltip();
    }

    /**
     * Mouse-anchored NEI tooltip for the hovered ingredient (recipe-grid stacks and port
     * pins), drawn in the foreground phase with depth off so nodes can never bury it. NEI's
     * own tooltip pass is suppressed on MUI screens while a widget is hovered.
     */
    private void drawHoveredIngredientTooltip() {
        // stackUnderMouse, not getStackForRecipeViewer: the NEI entry point also arms the
        // pending-lookup origin, and a per-frame tooltip must not mutate lookup state.
        if (!(getContext().getTopHovered() instanceof final RecipeNodeWidget nodeWidget)) return;
        final ItemStack stack = nodeWidget.stackUnderMouse();
        if (stack == null) return;
        final List<String> lines = GuiContainerManager.itemDisplayNameMultiline(stack, null, true);
        if (lines.isEmpty()) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        drawMultilineTip(getContext().getAbsMouseX() + 12, getContext().getAbsMouseY() - 12, lines);
        GL11.glPopAttrib();
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
        private static final int SECTION_TOGGLE_X_OFF = 12;
        private static final int SEPARATOR_Y_OFF = 2;
        private static final int MODE_TEXT_X = 6;
        private static final int MODE_LINE_H = 12;
        private static final int ZOOM_TEXT_X = 6;
        private static final int ZOOM_LINE_H = 14;
        private static final float NOTE_SCALE = 0.8f;

        private static final String[] HELP_LINES = { "[Scroll] zoom", "[LMB drag] move node", "[R/U] open NEI",
            "[+ in NEI GUI] add recipe" };

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
            size(WIDTH, 200);
        }

        /** Screen Y of each listed choice, filled during draw and read back on click. */
        private final List<int[]> choiceRows = new ArrayList<>();
        /** Screen Y span of each section header, same draw-then-read-back deal as the choices. */
        private final Map<SummarySection, int[]> headerRows = new EnumMap<>(SummarySection.class);

        /**
         * Nodes by descending operation count: the list is read for what to build most of, and the
         * tail is the part nobody scrolls to. Cached against the balance that ordered them - the
         * panel draws every frame and the counts only move when the chart is re-solved.
         */
        private BalanceResult sortedFor;
        private List<Node> byOperations = List.of();

        private List<Node> nodesByOperations(final BalanceResult br) {
            if (br == sortedFor) return byOperations;
            final List<Node> sorted = new ArrayList<>(
                graph().getNodes()
                    .values());
            sorted.sort(Comparator.comparingDouble((final Node node) -> {
                final NodeBalance nb = br.nodeBalances()
                    .get(node.getId());
                return nb == null ? 0 : nb.operations();
            })
                .reversed());
            sortedFor = br;
            byOperations = List.copyOf(sorted);
            return byOperations;
        }

        /**
         * Word-wrapped notes, held against the balance that produced them. Wrapping runs the font
         * renderer over every note and is asked for twice a frame - once to measure the panel and
         * again to draw it - where the notes only change when the chart is re-solved. The balance
         * object's identity is the same "has it been re-solved" proxy the router uses.
         */
        private BalanceResult wrappedFor;
        private List<MessageLine> wrappedMessages = List.of();

        private boolean choicesOffered(final BalanceResult br) {
            return BalanceView.hasChoices(graph());
        }

        /** Rows plus one heading per decision, but no heading when there is only one question. */
        private int choiceLineCount(final BalanceResult br) {
            if (!choicesOffered(br)) return 0;
            final BalanceView.Choices choices = graph().choices();
            final int headings = choices.groups()
                .size() > 1 ? choices.groups()
                    .size() : 0;
            return choices.rows()
                .size() + headings;
        }

        /** A null section is one that does not fold, and an unfoldable section is always open. */
        private boolean sectionOpen(@Nullable final SummarySection section) {
            return section == null || !graph().collapsedSummarySections.contains(section);
        }

        /** Header plus, when the section is open, one line per body row. */
        private int sectionHeight(@Nullable final SummarySection section, final int bodyLines) {
            return SECTION_H + (sectionOpen(section) ? bodyLines * LINE_H : 0) + SECTION_END_PAD;
        }

        /**
         * Machine-count lines: one per node that ran, plus the ops/cycle totals line. Zero when the
         * config hides the section, which is what keeps the height and the draw agreeing about it.
         */
        private int machineCountLines(final BalanceResult br) {
            if (Config.hideMachineCountsSection) return 0;
            int lines = br.totalOperations() > 0 || br.totalDurationTicks() > 0 ? 1 : 0;
            for (final Node node : graph().getNodes()
                .values()) {
                final NodeBalance nb = br.nodeBalances()
                    .get(node.getId());
                if (nb != null && nb.operations() > 0) lines++;
            }
            return lines;
        }

        private int computeHeight(final Summary summary, final BalanceResult br) {
            if (collapsed) return TITLE_H;
            int h = TITLE_H + SECTION_LY_OFFSET;

            if (choicesOffered(br)) {
                h += sectionHeight(null, choiceLineCount(br));
            }
            if (!summary.inputs()
                .isEmpty()) {
                h += sectionHeight(
                    null,
                    summary.inputs()
                        .size());
            }
            if (!summary.outputs()
                .isEmpty()) {
                h += sectionHeight(
                    null,
                    summary.outputs()
                        .size());
            }
            final int countLines = machineCountLines(br);
            if (countLines > 0) {
                h += sectionHeight(SummarySection.MACHINE_COUNTS, countLines);
            }
            if (!summary.properties()
                .isEmpty()) {
                h += sectionHeight(
                    SummarySection.STATISTICS,
                    summary.properties()
                        .size());
            }
            wrapNotes(br);
            if (!wrappedMessages.isEmpty()) {
                h += sectionHeight(SummarySection.MESSAGES, wrappedMessages.size());
            }
            h += MODE_LINE_H + ZOOM_LINE_H + sectionHeight(SummarySection.HELP, HELP_LINES.length) + SECTION_END_PAD;
            return h;
        }

        /**
         * Outputs/inputs whose amount survives display rounding: netting float residue would
         * otherwise print as a "0mB/s" line.
         */
        private Summary displayedSummary(final Summary s, final BalanceResult br) {
            final boolean isCycle = summaryMode() == SummaryMode.CYCLES;
            final float cycleSecs = summaryCycleSecs(br);
            return new Summary(
                visibleLines(s.outputs(), cycleSecs, isCycle),
                visibleLines(s.inputs(), cycleSecs, isCycle),
                s.properties());
        }

        private static List<Summary.Line<?>> visibleLines(final List<Summary.Line<?>> items, final float cycleSecs,
            final boolean isCycle) {
            final List<Summary.Line<?>> kept = new ArrayList<>();
            for (final var item : items) {
                final float shown = isCycle ? item.amount() : item.amount() / cycleSecs;
                // "Displays as zero" is the formatter's own call, whatever shape its output takes.
                if (!item.displayAmount(shown)
                    .equals(item.displayAmount(0f))) {
                    kept.add(item);
                }
            }
            return kept;
        }

        /** One drawn line of a solver message, coloured by the severity of the note it came from. */
        private record MessageLine(String text, int color) {}

        /**
         * Solver messages, word-wrapped to the summary width at the item text scale and coloured
         * per severity. One section for every severity: the tag on the front is finer than a
         * section heading can be, and cheaper than a fold per severity.
         */
        private void wrapNotes(final BalanceResult br) {
            if (br == wrappedFor) return;
            final List<MessageLine> lines = new ArrayList<>();
            final int wrapWidth = (int) ((WIDTH - ITEM_TEXT_X - 4) / NOTE_SCALE);
            for (final Note note : br.notes()) {
                final int color = severityColor(note.severity());
                for (final Object line : Minecraft.getMinecraft().fontRenderer
                    .listFormattedStringToWidth("- " + note.render(), wrapWidth)) {
                    lines.add(new MessageLine((String) line, color));
                }
            }
            wrappedFor = br;
            wrappedMessages = List.copyOf(lines);
        }

        private static int severityColor(final Severity severity) {
            return switch (severity) {
                case ERROR -> PlannhColors.ACCENT_RED.getColor();
                case WARN -> PlannhColors.ACCENT_AMBER.getColor();
                case INFO -> PlannhColors.ACCENT_BLUE.getColor();
            };
        }

        /** The loudest thing the solver said: an alarming bar over three asides cries wolf. */
        private static Severity loudest(final BalanceResult br) {
            Severity worst = Severity.INFO;
            for (final Note note : br.notes()) {
                final Severity severity = note.severity();
                if (severity.ordinal() < worst.ordinal()) worst = severity;
            }
            return worst;
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
            headerRows.clear();
            if (collapsed) {
                choiceRows.clear();
                return;
            }

            final Graph g = graph();
            final BalanceResult br = g.balance();
            final Summary s = displayedSummary(g.summary(), br);
            size(WIDTH, computeHeight(s, br));
            final SummaryMode sMode = summaryMode();
            final float cycleSecs = summaryCycleSecs(br);
            final boolean isCycle = sMode == SummaryMode.CYCLES;
            int ly = TITLE_H + SECTION_LY_OFFSET;

            // Chronological reading order: what was decided, what goes in, what comes out, what runs.
            ly = drawChoices(ly, w, br);

            ly = drawSection(
                ly,
                w,
                null,
                "Inputs",
                s.inputs(),
                PlannhColors.SECTION_INPUT.getColor(),
                PlannhColors.ACCENT_GREEN2.getColor(),
                PlannhColors.TEXT_MUTED.getColor(),
                cycleSecs,
                isCycle);

            ly = drawSection(
                ly,
                w,
                null,
                "Outputs",
                s.outputs(),
                PlannhColors.SECTION_PRODUCT.getColor(),
                PlannhColors.ACCENT_AMBER.getColor(),
                PlannhColors.ACCENT_AMBER.getColor(),
                cycleSecs,
                isCycle);

            ly = drawMachineCounts(ly, w, br, isCycle);

            ly = drawSection(
                ly,
                w,
                SummarySection.STATISTICS,
                "Statistics",
                s.properties(),
                PlannhColors.SECTION_OPS.getColor(),
                PlannhColors.ACCENT_BLUE.getColor(),
                PlannhColors.ACCENT_BLUE.getColor(),
                cycleSecs,
                isCycle);

            wrapNotes(br);
            if (!wrappedMessages.isEmpty()) {
                final Severity worst = loudest(br);
                ly = drawSectionHeader(
                    ly,
                    w,
                    SummarySection.MESSAGES,
                    "Solver Messages (" + br.notes()
                        .size() + ")",
                    worst == Severity.INFO ? PlannhColors.SECTION_OPS.getColor() : PlannhColors.SECTION_WARN.getColor(),
                    severityColor(worst));
                if (sectionOpen(SummarySection.MESSAGES)) {
                    for (final MessageLine line : wrappedMessages) {
                        GuiDraw.drawText(line.text(), ITEM_TEXT_X, ly, NOTE_SCALE, line.color(), false);
                        ly += LINE_H;
                    }
                }
                ly += SECTION_END_PAD;
            }

            // Below the fold: what the chart is, rather than what is in it.
            final BalanceMode mode = g.getBalanceMode();
            final String modeStr = String.format(
                StatCollector.translateToLocal("plannh.gui.balancer_mode"),
                String.format(mode.displayName(), g.isOpsMode() ? ", ops" : ""));
            GuiDraw.drawRect(0, ly - SEPARATOR_Y_OFF, w, 1, PlannhColors.SEPARATOR_LIGHT.getColor());
            GuiDraw.drawText(modeStr, MODE_TEXT_X, ly, 0.9f, PlannhColors.ACCENT_BLUE.getColor(), false);
            ly += MODE_LINE_H;
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

            ly = drawSectionHeader(
                ly,
                w,
                SummarySection.HELP,
                "Help",
                PlannhColors.SECTION_OPS.getColor(),
                PlannhColors.TEXT_MUTED.getColor());
            if (sectionOpen(SummarySection.HELP)) {
                for (final String line : HELP_LINES) {
                    GuiDraw.drawText(line, ITEM_TEXT_X, ly, 0.8f, PlannhColors.TEXT_FAINT.getColor(), false);
                    ly += LINE_H;
                }
            }
        }

        /**
         * The answers this chart could equally well have had, and which one is on screen.
         *
         * <p>
         * Shown because AUTO's job is a most reasonable DEFAULT: past the gate count every rule
         * that picked this answer over the others is a preference somebody encoded - voiding is
         * cheaper than importing, less material moved is better - and a preference the user cannot
         * see is one they cannot disagree with. Collapsed until asked for, because finding the
         * others costs a solve per candidate and drawing the chart must not.
         */
        private int drawChoices(int ly, final int w, final BalanceResult br) {
            choiceRows.clear();
            if (!choicesOffered(br)) return ly;

            final BalanceView.Choices alts = graph().choices();
            ly = drawSectionHeader(
                ly,
                w,
                null,
                "Choices (" + alts.rows()
                    .size() + ")",
                PlannhColors.SECTION_CHOICE.getColor(),
                PlannhColors.ACCENT_CYAN2.getColor());

            // A heading per decision only when there is more than one: with a single question the
            // heading would just repeat the row directly under it.
            final boolean headings = alts.groups()
                .size() > 1;
            for (final BalanceView.Group group : alts.groups()) {
                if (headings) {
                    GuiDraw.drawText(
                        group.heading()
                            .render() + ":",
                        ITEM_TEXT_X,
                        ly,
                        NOTE_SCALE,
                        PlannhColors.TEXT_MUTED.getColor(),
                        false);
                    ly += LINE_H;
                }
                for (final BalanceView.Choice row : group.rows()) {
                    GuiDraw.drawText(
                        (row.active() ? "> " : "  ") + (headings ? "  " : "")
                            + row.label()
                                .render(),
                        ITEM_TEXT_X,
                        ly,
                        NOTE_SCALE,
                        row.active() ? PlannhColors.ACCENT_CYAN2.getColor() : PlannhColors.TEXT_MUTED.getColor(),
                        false);
                    choiceRows.add(new int[] { ly, ly + LINE_H });
                    ly += LINE_H;
                }
            }
            return ly + SECTION_END_PAD;
        }

        /**
         * A section heading, clickable when the section folds. A folded section keeps its header,
         * or there would be nothing left to click to get it back; a null section draws the bar
         * alone, with no marker and no hit area.
         */
        private int drawSectionHeader(final int ly, final int w, @Nullable final SummarySection section,
            final String title, final int headerColor, final int titleColor) {
            GuiDraw.drawRect(SECTION_HEADER_X, ly, w - SECTION_HEADER_X * 2, SECTION_H, headerColor);
            GuiDraw.drawText(title, SECTION_HEADER_TEXT_X, ly + SECTION_HEADER_TEXT_Y_OFF, 1.0f, titleColor, false);
            if (section != null) {
                GuiDraw.drawText(
                    sectionOpen(section) ? "\u2212" : "+",
                    w - SECTION_TOGGLE_X_OFF,
                    ly + SECTION_HEADER_TEXT_Y_OFF,
                    1.0f,
                    titleColor,
                    false);
                headerRows.put(section, new int[] { ly, ly + SECTION_H });
            }
            return ly + SECTION_H;
        }

        /** Per-node operation counts and the run totals, folded away by default. */
        private int drawMachineCounts(int ly, final int w, final BalanceResult br, final boolean isCycle) {
            if (machineCountLines(br) == 0) return ly;
            ly = drawSectionHeader(
                ly,
                w,
                SummarySection.MACHINE_COUNTS,
                "Machine Counts",
                PlannhColors.SECTION_OPS.getColor(),
                PlannhColors.ACCENT_BLUE.getColor());
            if (!sectionOpen(SummarySection.MACHINE_COUNTS)) return ly + SECTION_END_PAD;

            for (final Node node : nodesByOperations(br)) {
                final NodeBalance nb = br.nodeBalances()
                    .get(node.getId());
                if (nb == null || nb.operations() <= 0) continue;
                GuiDraw.drawText(
                    "\u00d7" + GuiHelper.formatCount(nb.operations()) + "  " + node.getMachineName(),
                    ITEM_TEXT_X,
                    ly,
                    0.8f,
                    PlannhColors.TEXT_LIGHT.getColor(),
                    false);
                ly += LINE_H;
            }

            final StringBuilder totals = new StringBuilder();
            if (br.totalOperations() > 0) totals.append("Ops: ")
                .append(GuiHelper.formatCount(br.totalOperations()));
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
            if (!totals.isEmpty()) {
                GuiDraw.drawText(totals.toString(), ITEM_TEXT_X, ly, 0.8f, PlannhColors.ACCENT_BLUE.getColor(), false);
                ly += LINE_H;
            }
            return ly + SECTION_END_PAD;
        }

        private int drawSection(int ly, final int w, @Nullable final SummarySection section, final String title,
            final List<Summary.Line<?>> items, final int headerColor, final int titleColor, final int itemColor,
            final float cycleSecs, final boolean isCycle) {
            if (items.isEmpty()) return ly;
            ly = drawSectionHeader(ly, w, section, title + " (" + items.size() + ")", headerColor, titleColor);
            if (!sectionOpen(section)) return ly + SECTION_END_PAD;
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
                final Graph g = graph();
                final BalanceResult br = g.balance();
                size(WIDTH, computeHeight(displayedSummary(g.summary(), br), br));
                return Result.SUCCESS;
            }

            final Graph g0 = graph();
            final BalanceResult br0 = g0.balance();

            for (final var header : headerRows.entrySet()) {
                if (my < header.getValue()[0] || my >= header.getValue()[1]) continue;
                final var folded = graph().collapsedSummarySections;
                if (!folded.add(header.getKey())) folded.remove(header.getKey());
                PlanAPI.save();
                size(WIDTH, computeHeight(displayedSummary(g0.summary(), br0), br0));
                return Result.SUCCESS;
            }

            if (choicesOffered(br0)) {
                final List<BalanceView.Choice> options = g0.choices()
                    .rows();
                for (int i = 0; i < choiceRows.size() && i < options.size(); i++) {
                    if (my < choiceRows.get(i)[0] || my >= choiceRows.get(i)[1]) continue;
                    final ChoiceKey picked = options.get(i)
                        .key();
                    PlanAPI.recordEdit(g0, () -> g0.setExcessChoice(picked));
                    g0.markDirty();
                    PlanAPI.save();
                    return Result.SUCCESS;
                }
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
