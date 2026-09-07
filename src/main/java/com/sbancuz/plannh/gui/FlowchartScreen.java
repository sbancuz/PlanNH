package com.sbancuz.plannh.gui;

import static codechicken.lib.gui.GuiDraw.drawMultilineTip;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.value.EnumValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.menu.Menu;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.gui.components.MinimumsMenu;
import com.sbancuz.plannh.gui.summary.SummaryWidget;
import com.sbancuz.plannh.nei.NEIPlanConfig;

import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.guihook.GuiContainerManager;

public class FlowchartScreen extends ModularScreen {

    private static final int LEFT_MARGIN = 5;
    private static final int RIGHT_MARGIN = 15;
    private static final int TOP_MARGIN = 30;
    /** Where the button row ends, and so the highest a panel it opens may sit. */
    private static final int TOOLBAR_BOTTOM = TOP_MARGIN + 20;
    private static final int BOTTOM_MARGIN = 30;

    public static CanvasWidget canvas;

    private FlowchartScreen(final ModularPanel panel) {
        super(PlanNH.MODID, panel);
        getContext().setSettings(new UISettings());
        getContext().getUISettings()
            .getRecipeViewerSettings()
            .enable();
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

        canvas = new CanvasWidget(contextMenu, panel);

        // The structure every node in this chart opens on. One panel rather than three rows per node.
        final MinimumsMenu minimums = new MinimumsMenu();

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

        // Machine picker: the choices depend on which recipe the node holds, so the canvas refills
        // this list each time it opens rather than the children being fixed here.
        final Menu<?> machinePicker = new Menu<>();
        final ListWidget<IWidget, ?> machineList = new ListWidget<>().coverChildrenHeight()
            .width(140);
        machinePicker.setEnabledIf(_ -> canvas.isMachinePickerOpen())
            .coverChildren()
            .background()
            .relativeToScreen()
            .child(machineList);
        canvas.setMachinePicker(machinePicker, machineList);

        final TextFieldWidget slotNameField = new TextFieldWidget()
            .value(
                new StringValue.Dynamic(
                    () -> Plan.getActiveGraph()
                        .getName(),
                    val -> Plan.getActiveGraph()
                        .setName(val)))
            .background()
            .hoverBackground();

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
                        .overlay(
                            IKey.str("Add Note")
                                .color(Color.WHITE.main)))
                    .child(new ButtonWidget<>().onMousePressed(_ -> {
                        canvas.addGroup(canvas.getCanvasMouseX(), canvas.getCanvasMouseY());
                        return true;
                    })
                        .fullWidth()
                        .background(
                            new Rectangle().color(PlannhColors.CONTEXT_BG.getColor()),
                            new Rectangle().hollow()
                                .color(PlannhColors.CONTEXT_BORDER.getColor()))
                        .overlay(
                            IKey.str("Add Group")
                                .color(Color.WHITE.main)))
                    .child(new ButtonWidget<>().onMousePressed(_ -> {
                        canvas.addMachineGroup(canvas.getCanvasMouseX(), canvas.getCanvasMouseY());
                        return true;
                    })
                        .fullWidth()
                        .background(
                            new Rectangle().color(PlannhColors.CONTEXT_BG.getColor()),
                            new Rectangle().hollow()
                                .color(PlannhColors.CONTEXT_BORDER.getColor()))
                        .overlay(
                            IKey.lang("plannh.gui.group.add_machine_group")
                                .color(Color.WHITE.main))));

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
                            cycleGraphs(canvas, -1, slotNameField);
                            return true;
                        })
                            .overlay(IKey.str("<"))
                            .addTooltipLine("Previous Graph"))
                        .child(slotNameField)
                        .child(
                            IKey.dynamicKey(() -> IKey.str(slotPosition()))
                                .asWidget())
                        .child(new ButtonWidget<>().onMousePressed(_ -> {
                            cycleGraphs(canvas, 1, slotNameField);
                            return true;
                        })
                            .overlay(IKey.str(">"))
                            .addTooltipLine("Next Graph"))
                        .child(new ButtonWidget<>().onMousePressed(_ -> {
                            addGraph(canvas, slotNameField);
                            return true;
                        })
                            .overlay(
                                IKey.str("+")
                                    .color(Color.GREEN.main))
                            .addTooltipLine("Add Graph"))
                        .child(new ButtonWidget<>().onMousePressed(_ -> {
                            deleteGraph(canvas, slotNameField);
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
                                    PlanAPI.save();
                                    return true;
                                }))
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("Min"))
                                .tooltipStatic(
                                    t -> t.addLine(IKey.str("Structure this chart plans with"))
                                        .addLine(IKey.str("A node needing more raises itself")))
                                .onMousePressed(_ -> {
                                    minimums.toggle(
                                        canvas.getContext()
                                            .getAbsMouseX(),
                                        TOOLBAR_BOTTOM);
                                    return true;
                                }))
                        .child(
                            new ButtonWidget<>().overlay(IKey.str("S2G"))
                                .onMousePressed(_ -> {
                                    final Graph g = canvas.getGraph();
                                    g.setSnapToGrid(!g.isSnapToGrid());
                                    PlanAPI.save();
                                    return true;
                                }))
                        .child(
                            new CycleButtonWidget()
                                .value(
                                    new EnumValue.Dynamic<>(
                                        BalanceMode.class,
                                        () -> Plan.getActiveGraph()
                                            .getBalanceMode(),
                                        val -> Plan.getActiveGraph()
                                            .setBalanceMode(val)))
                                .stateOverlay(BalanceMode.NONE, IKey.str("M:-"))
                                .stateOverlay(BalanceMode.INPUT, IKey.str("M:F"))
                                .stateOverlay(BalanceMode.OUTPUT, IKey.str("M:B"))
                                .stateOverlay(BalanceMode.AUTO, IKey.str("M:A"))
                                .addTooltipLine("Cycle Balance Modes"))
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
        panel.child(targetEditor);
        panel.child(machinePicker);
        panel.child(minimums.widget());

        return new FlowchartScreen(panel);
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

    private static void refreshGraph(CanvasWidget canvas) {
        canvas.setGraph(Plan.getActiveGraph());
    }

    /**
     * The name box holds its own text once it has been drawn, so switching charts under it leaves
     * the previous name on screen. Push the new one in whenever the active chart changes.
     */
    private static void refreshGraph(final CanvasWidget canvas, final TextFieldWidget nameField) {
        refreshGraph(canvas);
        nameField.setText(
            Plan.getActiveGraph()
                .getName());
    }

    /** "(4/8)": which chart is on screen, and how many there are to page through. */
    private static String slotPosition() {
        final Plan plan = Plan.getInstance();
        final int count = Math.max(
            1,
            plan.getGraphs()
                .size());
        return "(" + Math.min(count, plan.getActiveIndex() + 1) + "/" + count + ")";
    }

    private static void cycleGraphs(CanvasWidget canvas, final int dir, final TextFieldWidget nameField) {
        final Plan plan = Plan.getInstance();
        final int size = plan.getGraphs()
            .size();
        if (size <= 1) return;
        plan.setActiveIndex((plan.getActiveIndex() + dir + size) % size);
        refreshGraph(canvas, nameField);
    }

    private static void addGraph(CanvasWidget canvas, final TextFieldWidget nameField) {
        final Plan plan = Plan.getInstance();
        final int size = plan.getGraphs()
            .size();
        plan.getGraphs()
            .add(new Graph("Slot " + (size + 1)));
        plan.setActiveIndex(size);
        refreshGraph(canvas, nameField);
    }

    private static void deleteGraph(CanvasWidget canvas, final TextFieldWidget nameField) {
        final Plan plan = Plan.getInstance();
        final int size = plan.getGraphs()
            .size();
        if (size <= 1) return;
        final int active = plan.getActiveIndex();
        plan.getGraphs()
            .remove(active);
        if (active >= size - 1) plan.setActiveIndex(size - 2);
        refreshGraph(canvas, nameField);
    }
}
