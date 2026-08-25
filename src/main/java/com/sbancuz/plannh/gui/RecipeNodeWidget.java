package com.sbancuz.plannh.gui;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;
import com.sbancuz.plannh.data.setting.SettingDef;
import com.sbancuz.plannh.layout.AutoLayout;
import com.sbancuz.plannh.nei.NodeLookupContext;

import codechicken.nei.PositionedStack;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.NEIRecipeWidget;
import codechicken.nei.recipe.RecipeHandlerRef;
import lombok.Getter;

public class RecipeNodeWidget extends Widget<RecipeNodeWidget>
    implements Interactable, RecipeViewerIngredientProvider, AutoLayout.LayoutNode {

    private static final int BASE_W = 120;
    private static final int BASE_H = 80;
    private static final int CLOSE_W = 12;
    private static final int CLOSE_MARGIN = 2;
    private static final int PORT_SIZE = 8;
    private static final int PORT_HALF = PORT_SIZE / 2;
    private static final int LINE_H = 11;
    private static final int ALPHA_BAR = 180;

    // NEI content area
    private static final int CONTENT_INSET = 5;
    private static final int CONTENT_TOP = 17;
    private static final int NEI_PAD_W = 10;
    private static final int NEI_PAD_H = 22;
    private static final int NEI_BORDER = 16;
    private static final int NEI_HANDLER_THROTTLE_MS = 50;

    // Texture background (9-patch)
    private static final int BORDER_9P = 9;
    private static final int TEXTURE_OFF = 4;
    private static final int TEXTURE_EXTRA = TEXTURE_OFF * 2;

    // Title bar
    private static final int TITLE_BAR_RMARGIN = 10;
    private static final int TITLE_BAR_H = 12;
    private static final int TITLE_UL_H = 1;
    private static final int TITLE_TEXT_Y = 7;

    // Header controls
    private static final int GROUP_LABEL_RMARGIN = 16;
    private static final int GEAR_X_RMARGIN = 14;
    private static final int GEAR_Y = 6;
    private static final int GEAR_HIT_LEFT_OFF = 18;
    private static final int GEAR_HIT_RIGHT_OFF = 6;
    private static final int GEAR_HIT_TOP = 4;
    private static final int GEAR_HIT_BOTTOM = 16;

    // Z-translation
    private static final int Z_PUSH = 400;
    private static final int Z_POP = -400;

    // Simple node (no NEI)
    private static final int SIMPLE_TEXT_INSET_X = 4;
    private static final int SIMPLE_TEXT_INSET_Y = 3;
    private static final int BOTTOM_TIMING_Y_FROM_BOTTOM = 12;
    private static final int ICON_SIZE = 16;
    private static final int ICON_RMARGIN = 4;
    private static final int ICON_Y = 14;
    private static final int SIMPLE_GROUP_LABEL_Y = 16;
    private static final int GROUP_BAR_W = 4;

    // Throughput / IO list
    private static final int LEFT_CONTENT_X = 8;
    private static final int THROUGHPUT_GAP = 4;
    private static final int LIST_INDENT = 4;

    // Settings panel
    private static final int CONFIG_PANEL_INSET = 2;
    private static final int CONFIG_PANEL_W = 170;
    private static final int BOOL_CLICK_W = 120;
    private static final int CLICK_H = 10;
    private static final int SETTING_DEC_X = 80;
    private static final int SETTING_BTN_W = 22;
    private static final int SETTING_INC_X = SETTING_DEC_X + SETTING_BTN_W;
    private static final int EXTRACTOR_BTN_W = 100;
    /** Usable width of a target row: the config panel minus its insets and a right pad. */
    private static final int TARGET_ROW_W = CONFIG_PANEL_W - 2 * CONFIG_PANEL_INSET - 8;
    private static final int HOLD_REPEAT_DELAY_MS = 350;
    private static final int HOLD_REPEAT_INTERVAL_MS = 50;

    private static final DrawableResource BG_TEXTURE = new DrawableBuilder(
        "nei:textures/gui/recipebg.png",
        0,
        0,
        184,
        174).build();

    @Nonnull
    @Getter
    private final Node node;
    @Nonnull
    private final CanvasWidget canvas;

    private boolean dragging = false;
    private int dragStartMouseX, dragStartMouseY;
    private int nodeStartX, nodeStartY;
    private String dragEditToken;

    private boolean doubleClickPending = false;
    private final GuiHelper.DoubleClickDetector doubleClick = new GuiHelper.DoubleClickDetector();

    @Nullable
    private RecipeHandlerRef handlerRef;
    @Nullable
    private NEIRecipeWidget neiWidget;
    private String recipeName = "";
    private boolean handlerInitFailed = false;
    private long lastHandlerUpdate = 0;
    private boolean configOpen = false;
    private final List<ClickZone> configZones = new ArrayList<>();
    private int hoverMx = -10000;
    private int hoverMy = -10000;

    private boolean zoneHeld = false;
    private int heldZoneMx, heldZoneMy;
    private long zoneHoldStart, zoneLastRepeat;

    private record ClickZone(int ux1, int uy1, int ux2, int uy2, Runnable action, boolean repeat) {

        ClickZone(final int ux1, final int uy1, final int ux2, final int uy2, final Runnable action) {
            this(ux1, uy1, ux2, uy2, action, false);
        }

        boolean contains(final int ux, final int uy) {
            return ux >= ux1 && ux < ux2 && uy >= uy1 && uy < uy2;
        }
    }

    public RecipeNodeWidget(final Node node, final CanvasWidget canvas) {
        this.node = node;
        this.canvas = canvas;
        final float z = canvas.getGraph()
            .getZoom();
        size(Math.round(BASE_W * z), Math.round(BASE_H * z));
    }

    @Override
    public UUID id() {
        return node.getId();
    }

    @Override
    public String machineName() {
        return node.getMachineName();
    }

    @Override
    public int inputCount() {
        return node.getInputs()
            .size();
    }

    @Override
    public int outputCount() {
        return node.getOutputs()
            .size();
    }

    @Override
    public int worldWidth() {
        if (handlerRef != null && neiWidget != null) {
            return neiWidget.w + NEI_PAD_W + NEI_BORDER;
        }
        return BASE_W;
    }

    @Override
    public int worldHeight() {
        if (handlerRef != null && neiWidget != null) {
            return neiWidget.h + NEI_PAD_H + calcInfoHeight() + computeConfigPanelHeight() + NEI_BORDER;
        }
        return BASE_H;
    }

    public void syncTransform(final float zoom, final float panX, final float panY) {
        final int sx = Math.round(node.getX() * zoom + panX);
        final int sy = Math.round(node.getY() * zoom + panY);
        pos(sx, sy);
        resizeForZoom(zoom);
    }

    private int zq(final float v) {
        return GuiHelper.zq(
            v,
            canvas.getGraph()
                .getZoom());
    }

    private int groupColor(final Group g) {
        return g.getColor();
    }

    @Nullable
    private Balancer.NodeBalance getNodeBalance() {
        final BalanceResult br = canvas.getGraph()
            .balance();
        return br.nodeBalances()
            .get(node.getId());
    }

    private int calcInfoHeight() {
        final int lines = 1 + node.getInputs()
            .size()
            + node.getOutputs()
                .size();
        return lines * LINE_H + 6;
    }

    /**
     * Loads the NEI handler that determines this widget's real size. Lazy - normally first
     * draw does it, but MUI2 culls off-viewport widgets, so anything measuring node sizes
     * (auto-layout) must call this first or off-screen nodes report stub dimensions.
     */
    public void ensureRecipeHandler() {
        if (handlerRef != null || handlerInitFailed) return;

        final RecipeHandlerRef ref = RecipeHandlerRef.of(node.getRecipeId());
        if (ref == null) {
            handlerInitFailed = true;
            return;
        }

        this.handlerRef = ref;
        this.neiWidget = new NEIRecipeWidget(ref);
        this.neiWidget.showAsWidget(true);
        this.neiWidget.x = CONTENT_INSET;
        this.neiWidget.y = CONTENT_TOP;

        this.recipeName = ref.handler.getRecipeName()
            .trim();
        resizeForZoom(
            canvas.getGraph()
                .getZoom());
    }

    private void setAreaSize(final int pw, final int ph) {
        getArea().width = pw;
        getArea().height = ph;
        size(pw, ph);
    }

    private void resizeForZoom(final float z) {
        if (handlerRef != null && neiWidget != null) {
            final int cw = worldWidth() - NEI_BORDER;
            final int ch = neiWidget.h + NEI_PAD_H + calcInfoHeight() + computeConfigPanelHeight();
            setAreaSize(Math.round((cw + NEI_BORDER) * z), Math.round((ch + NEI_BORDER) * z));
        } else {
            setAreaSize(Math.round(BASE_W * z), Math.round(BASE_H * z));
        }
    }

    @Override
    public void draw(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
        /*
         * ensureRecipeHandler();
         * // Widget-local mouse while hovered; parked far away otherwise so NEI's stack hover box
         * // only shows on the node actually under the mouse.
         * if (isHovering()) {
         * hoverMx = canvas.getCanvasMouseX() - Math.round(node.getX());
         * hoverMy = canvas.getCanvasMouseY() - Math.round(node.getY());
         * } else {
         * hoverMx = -10000;
         * hoverMy = -10000;
         * }
         * if (neiWidget != null && handlerRef != null) {
         * final long now = Minecraft.getSystemTime();
         * if (now - lastHandlerUpdate > NEI_HANDLER_THROTTLE_MS) {
         * lastHandlerUpdate = now;
         * handlerRef.handler.onUpdate();
         * }
         * glPushAttrib(GL_ENABLE_BIT | GL_LIGHTING_BIT | GL_COLOR_BUFFER_BIT);
         * glTranslatef(0, 0, Z_PUSH);
         * GuiContainerManager.enable2DRender();
         * glColor4f(1, 1, 1, 1);
         * final int cw = neiWidget.w + NEI_PAD_W;
         * final int ch = neiWidget.h + NEI_PAD_H;
         * BG_TEXTURE.draw(-TEXTURE_OFF, -TEXTURE_OFF, cw + TEXTURE_EXTRA, ch + TEXTURE_EXTRA, BORDER_9P, BORDER_9P,
         * BORDER_9P, BORDER_9P);
         * glEnable(GL_TEXTURE_2D);
         * final int titleCol = PlannhColors.titleColor(recipeName);
         * GuiDraw.drawRect(CONTENT_INSET, CONTENT_INSET, cw - TITLE_BAR_RMARGIN, TITLE_BAR_H, titleCol);
         * GuiDraw.drawRect(CONTENT_INSET, CONTENT_TOP, cw - TITLE_BAR_RMARGIN, TITLE_UL_H,
         * PlannhColors.NODE_TITLE_LINE.getColor());
         * final int titleW = Minecraft.getMinecraft().fontRenderer.getStringWidth(recipeName);
         * GuiDraw.drawText(
         * recipeName,
         * (float) neiWidget.w / 2 - (float) titleW / 2,
         * TITLE_TEXT_Y,
         * 1.0f,
         * PlannhColors.textOn(titleCol),
         * false);
         * if (node.getMachineConfig().hasAnyBoost()) {
         * GuiDraw.drawText(buildConfigBadge(), LEFT_CONTENT_X, TITLE_TEXT_Y, 1.0f, PlannhColors.TEXT_BADGE.getColor(),
         * false);
         * }
         * final Group grp = canvas.getGroupForNode(node.getId());
         * if (grp != null) {
         * final int gc = groupColor(grp);
         * final String gl = "\u229f " + grp.getHeader();
         * final int glW = Minecraft.getMinecraft().fontRenderer.getStringWidth(gl);
         * GuiDraw.drawText(gl, cw - glW - GROUP_LABEL_RMARGIN, TITLE_TEXT_Y, 1.0f, gc, false);
         * }
         * GuiDraw.drawText(
         * "\u2699",
         * cw - GEAR_X_RMARGIN,
         * GEAR_Y,
         * 1.0f,
         * configOpen ? PlannhColors.ACCENT_GREEN.getColor() : PlannhColors.TEXT_DIM.getColor(),
         * false);
         * // Real mouse position enables NEI's own hover box on recipe stacks.
         * neiWidget.draw(hoverMx, hoverMy);
         * drawThroughputInfo();
         * drawConfigContent();
         * repeatHeldZone();
         * drawCloseButtonPixel(getArea().width, getArea().height);
         * drawPorts();
         * drawGroupMembershipBar();
         * glPopAttrib();
         * glTranslatef(0, 0, Z_POP);
         * } else {
         * final int w = getArea().width;
         * final int h = getArea().height;
         * GuiDraw.drawRect(0, 0, w, h, PlannhColors.NODE_BG.getColor());
         * GuiHelper.drawRectBorder(0, 0, w, h, 1, PlannhColors.NODE_BORDER.getColor());
         * assert node.getMachineName() != null;
         * GuiDraw.drawText(
         * node.getMachineName().isEmpty() ? "?" : node.getMachineName(),
         * zq(SIMPLE_TEXT_INSET_X),
         * zq(SIMPLE_TEXT_INSET_Y),
         * z,
         * PlannhColors.TEXT_LIGHT.getColor(),
         * false);
         * final NodeBalance simpleNb = getNodeBalance();
         * final int simpleOps = simpleNb != null ? simpleNb.operations : 1;
         * final int simpleDurPerOp = simpleNb != null ? simpleNb.durationPerOp : node.getDurationTicks();
         * final StringBuilder simpleTiming = new StringBuilder();
         * if (simpleOps > 0) {
         * simpleTiming.append("\u00d7")
         * .append(GuiHelper.formatCount(simpleOps));
         * }
         * if (simpleDurPerOp > 0) {
         * if (!simpleTiming.isEmpty()) simpleTiming.append("  ");
         * simpleTiming.append(simpleDurPerOp)
         * .append("t (")
         * .append(String.format("%.1f", (float) simpleDurPerOp / GuiHelper.TICKS_PER_SECOND))
         * .append("s)");
         * }
         * final Object rawVoltage = node.getMachineConfig().getSettings().get("voltage");
         * if (rawVoltage instanceof final String v && !"OFF".equals(v)) {
         * simpleTiming.append("  ").append(v);
         * }
         * GuiDraw.drawText(
         * simpleTiming.toString(),
         * SIMPLE_TEXT_INSET_X,
         * h - BOTTOM_TIMING_Y_FROM_BOTTOM,
         * 1.0f,
         * PlannhColors.ACCENT_BLUE.getColor(),
         * false);
         * final ItemStack primary = getFirstItemOutput();
         * if (primary != null) {
         * final int is = ICON_SIZE;
         * GuiDraw.drawItem(primary, w - is - ICON_RMARGIN, ICON_Y, is, is, context.getCurrentDrawingZ());
         * }
         * final Group grp2 = canvas.getGroupForNode(node.getId());
         * if (grp2 != null) {
         * GuiDraw.drawText("\u229f " + grp2.getHeader(), SIMPLE_TEXT_INSET_X, SIMPLE_GROUP_LABEL_Y, 1.0f,
         * groupColor(grp2), false);
         * }
         * drawCloseButtonPixel(w, h);
         * drawPorts();
         * drawGroupMembershipBar();
         * }
         */
    }

    private void drawCloseButtonPixel(final int w, final int h) {
        /*
         * GuiHelper.drawCloseButton(
         * canvas.getGraph()
         * .getZoom(),
         * w,
         * CLOSE_W,
         * CLOSE_MARGIN,
         * PlannhColors.BTN_DELETE_BG.getColor(),
         * PlannhColors.ACCENT_RED_X.getColor());
         */
    }

    public int getOutputPortAt(final int mx, final int my) {
        final int half = zq(PORT_HALF);
        final int px = getArea().width - zq(PORT_SIZE);
        for (int i = 0; i < node.getOutputs()
            .size(); i++) {
            final int py = portTopY(i) - half;
            if (mx >= px && mx < px + PORT_SIZE && my >= py && my < py + PORT_SIZE) return i;
        }
        return -1;
    }

    public int getInputPortAt(final int mx, final int my) {
        final int half = zq(PORT_HALF);
        for (int i = 0; i < node.getInputs()
            .size(); i++) {
            final int py = portTopY(i) - half;
            if (mx >= 0 && mx < PORT_SIZE && my >= py && my < py + PORT_SIZE) return i;
        }
        return -1;
    }

    private void drawGroupMembershipBar() {
        final Group group = canvas.getGroupForNode(node.getId());
        if (group == null) return;
        final int gc = groupColor(group);
        GuiDraw.drawRect(
            0,
            0,
            GROUP_BAR_W,
            getArea().height,
            Color.argb(Color.getRed(gc), Color.getGreen(gc), Color.getBlue(gc), ALPHA_BAR));
    }

    private int portTopY(final int index) {
        return PortGeometry.portY(index);
    }

    private void drawPorts() {
        final int ps = PORT_SIZE;
        final int half = PORT_HALF;

        for (int i = 0; i < node.getOutputs()
            .size(); i++) {
            final int py = portTopY(i) - half;
            final int color = node.getOutputs()
                .get(i)
                .getPinColor(false);
            // Contrast outline keeps the pin visible against any world background.
            GuiDraw.drawRect(getArea().width - ps - 1, py - 1, ps + 2, ps + 2, IngredientColors.outlineFor(color));
            GuiDraw.drawRect(getArea().width - ps, py, ps, ps, color);
        }
        for (int i = 0; i < node.getInputs()
            .size(); i++) {
            final int py = portTopY(i) - half;
            final int color = node.getInputs()
                .get(i)
                .getPinColor(true);
            GuiDraw.drawRect(-1, py - 1, ps + 2, ps + 2, IngredientColors.outlineFor(color));
            GuiDraw.drawRect(0, py, ps, ps, color);
        }
    }

    private void drawThroughputInfo() {
        /*
         * if (neiWidget == null) return;
         * final int x = LEFT_CONTENT_X;
         * int y = CONTENT_TOP + neiWidget.h + THROUGHPUT_GAP;
         * final Balancer.NodeBalance nb = getNodeBalance();
         * final float sec = nb != null && nb.totalDurationTicks() > 0
         * ? (float) nb.totalDurationTicks() / GuiHelper.TICKS_PER_SECOND
         * : node.getRecipeDuration() > 0 ? (float) node.getRecipeDuration() / GuiHelper.TICKS_PER_SECOND : 1f;
         * final double ops = nb != null ? nb.operations() : 1;
         * final int durPerOp = nb != null ? nb.durationPerOp() : node.getDurationTicks();
         * final StringBuilder opsLine = new StringBuilder();
         * // No balance (unpinned Auto): show the recipe duration only - no count, and below, no
         * // throughput rows. An unpinned chart is wiring, not a solved plan; per-machine rates
         * // would be numbers with no anchor.
         * if (ops > 0) {
         * opsLine.append("\u00d7")
         * .append(GuiHelper.formatCount(ops));
         * }
         * if (durPerOp > 0) {
         * if (!opsLine.isEmpty()) opsLine.append("  ");
         * opsLine.append(durPerOp)
         * .append("t (")
         * .append(String.format("%.2f", (float) durPerOp / GuiHelper.TICKS_PER_SECOND))
         * .append("s)");
         * }
         * GuiDraw.drawText(opsLine.toString(), x, y, 1.0f, PlannhColors.ACCENT_BLUE.getColor(), false);
         * y += LINE_H;
         * if (ops <= 0) return;
         * y = drawPortList(x, y, node.getInputs(), nb, sec, false);
         * drawPortList(x, y, node.getOutputs(), nb, sec, true);
         */
    }

    private int drawPortList(final int x, int y, final List<Port<?>> ports, final Balancer.NodeBalance nb,
        final float sec, final boolean output) {
        for (int i = 0; i < ports.size(); i++) {
            final Port<?> port = ports.get(i);
            final String label = portLabel(port, i, nb, sec, output);
            if (label == null) continue;
            GuiDraw.drawText(label, x + (output ? LIST_INDENT : 0), y, 1.0f, portColor(port, output), false);
            y += LINE_H;
        }
        return y;
    }

    @Nullable
    private String portLabel(final Port<?> port, final int index, final Balancer.NodeBalance nb, final float sec,
        final boolean output) {
        if (!hasVisibleAmount(port)) return null;
        // Both directions read the balance's effective totals so exact rates sit next to exact
        // rates on the same node.
        final float total = effectiveTotal(nb, index, output, port.getAmount());
        String label = port.getType()
            .formatAmount(total / sec) + "/s "
            + port.getDisplayName();
        if (output && port.getChance() < 0.999f) {
            label += " (" + Math.round(port.getAmount() * port.getChance() * 100) + "%)";
        }
        return label;
    }

    private static float effectiveTotal(final Balancer.NodeBalance nb, final int index, final boolean output,
        final float fallbackPerOp) {
        final var effective = output ? nb.effectiveOutputs() : nb.effectiveInputs();
        final Float total = effective.get(index);
        return total != null ? total : fallbackPerOp;
    }

    private static int portColor(final Port<?> port, final boolean output) {
        if (port.getType() == RecipePropertyAPI.FLUID) {
            return output ? PlannhColors.ACCENT_CYAN.getColor() : PlannhColors.ACCENT_BLUE3.getColor();
        }
        return output ? PlannhColors.ACCENT_YELLOW.getColor() : PlannhColors.TEXT_MUTED.getColor();
    }

    @Override
    public @Nonnull Result onMousePressed(final int mouseButton) {
        /*
         * if (mouseButton == 0) {
         * final int mx = getContext().getMouseX();
         * final int my = getContext().getMouseY();
         * if (GuiHelper.isInsideCloseButton(
         * mx,
         * my,
         * canvas.getGraph()
         * .getZoom(),
         * getArea().width,
         * CLOSE_W,
         * CLOSE_MARGIN)) {
         * canvas.removeNode(node.getId());
         * return Result.SUCCESS;
         * }
         * if (neiWidget != null) {
         * final int cw = neiWidget.w + NEI_PAD_W;
         * if (mx >= cw - GEAR_HIT_LEFT_OFF && mx <= cw - GEAR_HIT_RIGHT_OFF
         * && my >= GEAR_HIT_TOP
         * && my <= GEAR_HIT_BOTTOM) {
         * configOpen = !configOpen;
         * resizeForZoom(
         * canvas.getGraph()
         * .getZoom());
         * return Result.SUCCESS;
         * }
         * if (configOpen) {
         * for (final ClickZone zone : configZones) {
         * if (zone.contains(mx, my)) {
         * // Held repeats mutate outside this bracket and fold into this entry.
         * PlanAPI.recordEdit(canvas.getGraph(), zone.action);
         * if (zone.repeat()) {
         * zoneHeld = true;
         * heldZoneMx = mx;
         * heldZoneMy = my;
         * zoneHoldStart = System.currentTimeMillis();
         * zoneLastRepeat = zoneHoldStart;
         * }
         * return Result.SUCCESS;
         * }
         * }
         * }
         * }
         * if (getOutputPortAt(mx, my) >= 0) return Result.IGNORE;
         * if (doubleClick.check()) {
         * doubleClickPending = true;
         * return Result.SUCCESS;
         * }
         * doubleClickPending = false;
         * dragging = true;
         * dragEditToken = PlanAPI.undoHistory()
         * .beginEdit(canvas.getGraph());
         * dragStartMouseX = getContext().getAbsMouseX();
         * dragStartMouseY = getContext().getAbsMouseY();
         * nodeStartX = node.getX();
         * nodeStartY = node.getY();
         * return Result.SUCCESS;
         * }
         */
        return Result.IGNORE;
    }

    @Override
    public boolean onMouseRelease(final int mouseButton) {
        if (mouseButton == 0) {
            zoneHeld = false;
            if (doubleClickPending) {
                doubleClickPending = false;
                openNeiRecipe();
                return true;
            }
            dragging = false;
            canvas.recheckMembershipAndFit();
            PlanAPI.undoHistory()
                .commitEdit(dragEditToken, canvas.getGraph());
            dragEditToken = null;
            return true;
        }
        return false;
    }

    @Override
    public void onMouseDrag(final int mouseButton, final long timeSinceClick) {
        if (dragging && mouseButton == 0) {
            final int dx = getContext().getAbsMouseX() - dragStartMouseX;
            final int dy = getContext().getAbsMouseY() - dragStartMouseY;
            final float z = canvas.getGraph()
                .getZoom();
            node.setX(nodeStartX + Math.round(dx / z));
            node.setY(nodeStartY + Math.round(dy / z));
            // canvas.clampNodeToGroup(node);
            syncTransform(
                z,
                canvas.getGraph()
                    .getPanX(),
                canvas.getGraph()
                    .getPanY());
        }
    }

    private String buildConfigBadge() {
        final MachineConfig c = node.getMachineConfig();
        final MachineProfile profile = c.getProfile();
        final StringBuilder sb = new StringBuilder();

        for (final SettingDef<?> def : profile
            .visibleSettings(new RecipeContext(node.getProperties()), c.getSettings())) {
            final Object val = c.getSettings()
                .get(def.getKey());
            if (val == null) continue;
            if (val.equals(def.getDefaultValue())) continue;
            final String badge = def.badge(val, c);
            if (badge == null) continue;
            sb.append(badge)
                .append(' ');
        }

        if (!sb.isEmpty()) sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private void drawConfigContent() {
        configZones.clear();
        if (!configOpen) return;
        assert neiWidget != null;

        final int x = LEFT_CONTENT_X;
        final int y0 = CONTENT_TOP + neiWidget.h + THROUGHPUT_GAP + calcInfoHeight();
        final MachineProfile profile = node.getMachineConfig()
            .getProfile();
        final int panelH = configRowsHeight() + 4;
        GuiDraw.drawRect(
            x - CONFIG_PANEL_INSET,
            y0 - CONFIG_PANEL_INSET,
            CONFIG_PANEL_W,
            panelH,
            PlannhColors.SETTINGS_PANEL_BG.getColor());

        final MachineConfig c = node.getMachineConfig();
        int y = y0;

        // Fixed toggle
        final boolean fixed = node.isMachineCountFixed();
        final String fixedLabel = (fixed ? "[\u2713] " : "[  ] ") + "Fixed";
        GuiDraw.drawText(
            fixedLabel,
            x,
            y,
            1.0f,
            fixed ? PlannhColors.SETTING_ON.getColor() : PlannhColors.SETTING_OFF.getColor(),
            false);
        configZones.add(new ClickZone(x, y, x + BOOL_CLICK_W, y + CLICK_H, () -> {
            node.setMachineCountFixed(!fixed);
            onConfigChanged();
        }));
        y += LINE_H;

        for (final SettingDef<?> def : profile
            .visibleSettings(new RecipeContext(node.getProperties()), c.getSettings())) {
            y = drawSetting(x, y, def, c);
        }

        // One row per output: pin the rate the chart should produce. The row opens a text
        // editor; rates are typed, not stepped.
        final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        for (final int idx : targetableOutputs()) {
            final double current = node.getTargetOutputRates()
                .getOrDefault(idx, 0.0);
            // Scale suffixes but not the port's formatter: this row is what the rate editor writes
            // back, and the editor takes a plain number. "1.2k" is the same quantity as 1200, but a
            // fluid's "1.0B" is 1000 litres in a field that wants litres.
            final String value = current > 0 ? GuiHelper.formatRate((float) current) + "/s" : "off";
            final int valueW = font.getStringWidth(value);
            final String label = font.trimStringToWidth(
                "Tgt " + node.getOutputs()
                    .get(idx)
                    .getDisplayName(),
                TARGET_ROW_W - valueW - 6);
            GuiDraw.drawText(label, x, y, 1.0f, PlannhColors.TEXT_LIGHT.getColor(), false);
            GuiDraw.drawText(
                value,
                x + TARGET_ROW_W - valueW,
                y,
                1.0f,
                current > 0 ? PlannhColors.SETTING_ON.getColor() : PlannhColors.TEXT_MUTED.getColor(),
                false);
            final int out = idx;
            configZones
                .add(new ClickZone(x, y, x + TARGET_ROW_W, y + CLICK_H, () -> canvas.openTargetEditor(node, out)));
            y += LINE_H;
        }

        if (node.getAvailableExtractors()
            .size() > 1) {
            final String label = "[\u00AB] " + node.getExtractor()
                .getExtractorName() + " [\u00BB]";
            GuiDraw.drawText(label, x, y, 1.0f, PlannhColors.ACCENT_GREEN.getColor(), false);
            configZones.add(new ClickZone(x, y, x + EXTRACTOR_BTN_W, y + CLICK_H, () -> {
                node.switchExtractor();
                onConfigChanged();
            }));
        }
    }

    /**
     * Re-fires the held [-]/[+] zone. Zones are rebuilt every frame, so re-hit-testing the
     * press point picks up the closure holding the current value.
     */
    private void repeatHeldZone() {
        if (!zoneHeld || !configOpen) return;
        final long now = System.currentTimeMillis();
        if (now - zoneHoldStart < HOLD_REPEAT_DELAY_MS || now - zoneLastRepeat < HOLD_REPEAT_INTERVAL_MS) return;
        for (final ClickZone zone : configZones) {
            if (zone.repeat() && zone.contains(heldZoneMx, heldZoneMy)) {
                zone.action.run();
                zoneLastRepeat = now;
                return;
            }
        }
    }

    private int drawSetting(final int x, final int y, final SettingDef<?> def, final MachineConfig c) {
        /*
         * if (def.type == Integer.class) {
         * return drawConfigIntField(x, y, def.label, c.getInt(def.key), def.minInt, def.maxInt, v -> {
         * c.setInt(def.key, v);
         * onConfigChanged();
         * });
         * } else if (def.type == Boolean.class) {
         * final boolean val = c.getBoolean(def.key);
         * final String label = (val ? "[\u2713] " : "[  ] ") + def.label;
         * GuiDraw.drawText(
         * label,
         * x,
         * y,
         * 1.0f,
         * val ? PlannhColors.SETTING_ON.getColor() : PlannhColors.SETTING_OFF.getColor(),
         * false);
         * configZones.add(new ClickZone(x, y, x + BOOL_CLICK_W, y + CLICK_H, () -> {
         * c.setBoolean(def.key, !val);
         * onConfigChanged();
         * }));
         * return y + LINE_H;
         * } else if (def.type == String.class && def.hasOptions()) {
         * final String val = c.getString(def.key);
         * GuiDraw.drawText(def.label + " " + val, x, y, 1.0f, PlannhColors.SETTING_ON.getColor(), false);
         * final int decX = x + SETTING_DEC_X;
         * final int incX = decX + SETTING_BTN_W;
         * GuiDraw.drawText("[-]", decX, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);
         * GuiDraw.drawText("[+]", incX, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);
         * assert def.options != null;
         * configZones.add(new ClickZone(decX, y, incX, y + CLICK_H, () -> {
         * final int cur = def.options.indexOf(c.getString(def.key));
         * c.setString(def.key, def.options.get(Math.max(0, (Math.max(cur, 0)) - 1)));
         * onConfigChanged();
         * }, true));
         * configZones.add(new ClickZone(incX, y, incX + SETTING_BTN_W, y + CLICK_H, () -> {
         * final int cur = def.options.indexOf(c.getString(def.key));
         * c.setString(def.key, def.options.get(Math.min(def.options.size() - 1, (Math.max(cur, 0)) + 1)));
         * onConfigChanged();
         * }, true));
         * return y + LINE_H;
         * }
         */
        return 0;
    }

    private int computeConfigPanelHeight() {
        return configOpen ? configRowsHeight() + 8 : 0;
    }

    private int configRowsHeight() {
        int h = (node.getMachineConfig()
            .getProfile()
            .visibleSettings(
                new RecipeContext(node.getProperties()),
                node.getMachineConfig()
                    .getSettings())
            .size() + 2
            + targetableOutputs().size()) * LINE_H;
        if (node.getAvailableExtractors()
            .size() > 1) h += LINE_H;
        return h;
    }

    /** Output indices that get a target row: the same ports the throughput list shows. */
    private List<Integer> targetableOutputs() {
        final List<Integer> result = new ArrayList<>();
        for (int i = 0; i < node.getOutputs()
            .size(); i++) {
            if (hasVisibleAmount(
                node.getOutputs()
                    .get(i))) {
                result.add(i);
            }
        }
        return result;
    }

    private int drawConfigIntField(final int x, final int y, final String label, final int value, final int min,
        final int max, final IntConsumer setter) {
        GuiDraw.drawText(label + " " + value, x, y, 1.0f, PlannhColors.TEXT_LIGHT.getColor(), false);
        GuiDraw.drawText("[-]", x + SETTING_DEC_X, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);
        GuiDraw.drawText("[+]", x + SETTING_INC_X, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);

        configZones.add(
            new ClickZone(
                x + SETTING_DEC_X,
                y,
                x + SETTING_INC_X,
                y + CLICK_H,
                () -> { if (value > min) setter.accept(value - 1); },
                true));
        configZones.add(
            new ClickZone(
                x + SETTING_INC_X,
                y,
                x + SETTING_INC_X + SETTING_BTN_W,
                y + CLICK_H,
                () -> { if (value < max) setter.accept(value + 1); },
                true));
        return y + LINE_H;
    }

    private void onConfigChanged() {
        canvas.getGraph()
            .markDirty();
        resizeForZoom(
            canvas.getGraph()
                .getZoom());
    }

    @Nullable
    private ItemStack getFirstItemOutput() {
        for (final Port<?> port : node.getOutputs()) {
            if (port.getType() == RecipePropertyAPI.ITEM) return (ItemStack) port.getValue();
        }
        return null;
    }

    private void openNeiRecipe() {
        for (final Port<?> port : node.getOutputs()) {
            if (port.getType() == RecipePropertyAPI.ITEM && port.getValue() != null) {
                GuiCraftingRecipe.openRecipeGui("item", port.getValue());
                return;
            }
        }
    }

    /**
     * Tells NEI what ingredient the mouse is over (enables R/U and bookmarks on the embedded
     * recipe's stacks and the port pins).
     */
    @Override
    @Nullable
    public ItemStack getStackForRecipeViewer() {
        // NEI asks via GuiContainerManager.getStackMouseOver, which AE2 also fires from
        // lastKeyTyped - AFTER the key was handled. Closing the screen with E disposes this
        // widget inside that same key event, so the query can arrive on a dead widget.
        if (!isValid()) return null;
        final IngredientHit hit = ingredientUnderMouse();
        if (hit == null) return null;
        canvas.setPendingLookup(hit.origin());
        return hit.stack();
    }

    @Nullable
    public ItemStack stackUnderMouse() {
        if (!isValid()) return null;
        final IngredientHit hit = ingredientUnderMouse();
        return hit == null ? null : hit.stack();
    }

    /**
     * A hovered ingredient: the display stack NEI operates on, plus the port it came from -
     * null when an embedded grid stack is no port's ingredient (catalysts, machine items).
     */
    private record IngredientHit(ItemStack stack, @Nullable NodeLookupContext origin) {}

    @Nullable
    private IngredientHit ingredientUnderMouse() {
        // World-space widget-local mouse; independent of whatever viewport state NEI calls us in.
        final int mx = canvas.getCanvasMouseX() - Math.round(node.getX());
        final int my = canvas.getCanvasMouseY() - Math.round(node.getY());

        if (neiWidget != null && handlerRef != null) {
            final PositionedStack gridStack = neiWidget.getPositionedStackMouseOver(mx, my);
            if (gridStack != null) {
                return new IngredientHit(gridStack.item, portOriginFor(gridStack.item, gridSlotIsInput(gridStack)));
            }
        }

        // final int out = getOutputPortAt(mx, my);
        // if (out >= 0) return hitFor(
        // node.getOutputs()
        // .get(out),
        // true,
        // out);
        // final int in = getInputPortAt(mx, my);
        // if (in >= 0) return hitFor(
        // node.getInputs()
        // .get(in),
        // false,
        // in);
        return null;
    }

    @Nullable
    private IngredientHit hitFor(final Port<?> port, final boolean output, final int index) {
        // final ItemStack stack = port.getDisplayStack();
        // if (stack == null) return null;
        // return new IngredientHit(stack, new NodeLookupContext(node.getId(), output, index));
        return null;
    }

    /** Whether a hovered grid slot sits on the recipe's input side, by slot position. */
    private boolean gridSlotIsInput(final PositionedStack hit) {
        assert handlerRef != null;
        for (final PositionedStack s : handlerRef.handler.getIngredientStacks(handlerRef.recipeIndex)) {
            if (s != null && s.relx == hit.relx && s.rely == hit.rely) return true;
        }
        return false;
    }

    /**
     * Which port an embedded recipe-grid stack refers to, by forward display-stack comparison
     * (covers fluids: GT display items encode the fluid in the damage value). The hovered
     * slot's side disambiguates recipes carrying the same ingredient as both input and output;
     * the opposite side is a fallback for slots that are no port of their own (e.g. catalysts).
     */
    @Nullable
    private NodeLookupContext portOriginFor(final ItemStack gridStack, final boolean inputSide) {
        final NodeLookupContext sameSide = portMatching(gridStack, !inputSide);
        return sameSide != null ? sameSide : portMatching(gridStack, inputSide);
    }

    @Nullable
    private NodeLookupContext portMatching(final ItemStack stack, final boolean output) {
        final List<Port<?>> ports = output ? node.getOutputs() : node.getInputs();
        // for (int i = 0; i < ports.size(); i++) {
        // if (displayMatches(ports.get(i), stack)) return new NodeLookupContext(node.getId(), output, i);
        // }
        return null;
    }

    private static boolean displayMatches(final Port<?> port, final ItemStack stack) {
        // final ItemStack display = port.getDisplayStack();
        // return display != null && display.isItemEqual(stack);
        return false;
    }

    /** Whether the port draws a throughput row: it holds a value with a positive amount. */
    private static boolean hasVisibleAmount(final Port<?> port) {
        if (port.getType() == RecipePropertyAPI.ITEM) {
            final ItemStack stack = (ItemStack) port.getValue();
            return stack != null && stack.stackSize > 0;
        }
        if (port.getType() == RecipePropertyAPI.FLUID) {
            final FluidStack fs = (FluidStack) port.getValue();
            return fs != null && fs.amount > 0;
        }
        return false;
    }

}
