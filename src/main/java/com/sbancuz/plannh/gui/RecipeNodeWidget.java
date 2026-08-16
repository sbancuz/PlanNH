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
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;
import com.sbancuz.plannh.layout.AutoLayout;
import com.sbancuz.plannh.nei.NodeLookupContext;

import codechicken.nei.PositionedStack;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
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
    /** Breathing room between a setting's text and the [-]/[+] steppers that follow it. */
    private static final int SETTING_LABEL_GAP = 4;
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

    /**
     * @param resetKey the setting this zone edits, or null when the zone is not a setting row.
     *                 Right-clicking a zone that has one returns that setting to the machine.
     */
    private record ClickZone(int ux1, int uy1, int ux2, int uy2, Runnable action, boolean repeat,
        @Nullable String resetKey) {

        ClickZone(final int ux1, final int uy1, final int ux2, final int uy2, final Runnable action) {
            this(ux1, uy1, ux2, uy2, action, false, null);
        }

        ClickZone(final int ux1, final int uy1, final int ux2, final int uy2, final Runnable action,
            final boolean repeat) {
            this(ux1, uy1, ux2, uy2, action, repeat, null);
        }

        ClickZone withReset(final String key) {
            return new ClickZone(ux1, uy1, ux2, uy2, action, repeat, key);
        }

        boolean contains(final int ux, final int uy) {
            return ux >= ux1 && ux < ux2 && uy >= uy1 && uy < uy2;
        }
    }

    public RecipeNodeWidget(final Node node, final CanvasWidget canvas) {
        this.node = node;
        this.canvas = canvas;
        size(BASE_W, BASE_H);
    }

    @Override
    public UUID id() {
        return node.id;
    }

    @Override
    public String machineName() {
        return node.machineName;
    }

    @Override
    public int inputCount() {
        return node.inputs.size();
    }

    @Override
    public int outputCount() {
        return node.outputs.size();
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
        pos(Math.round(node.x), Math.round(node.y));
        resizeForZoom(zoom);
    }

    private int groupColor(final Group g) {
        return g.getColor();
    }

    @Nullable
    private Balancer.NodeBalance getNodeBalance() {
        final BalanceResult br = canvas.getGraph()
            .balance();
        return br.nodeBalances()
            .get(node.id);
    }

    private int calcInfoHeight() {
        final int lines = 1 + node.inputs.size() + node.outputs.size();
        return lines * LINE_H + 6;
    }

    /**
     * Loads the NEI handler that determines this widget's real size. Lazy - normally first
     * draw does it, but MUI2 culls off-viewport widgets, so anything measuring node sizes
     * (auto-layout) must call this first or off-screen nodes report stub dimensions.
     */
    public void ensureRecipeHandler() {
        if (handlerRef != null || handlerInitFailed) return;

        final RecipeHandlerRef ref = RecipeHandlerRef.of(node.recipeId);
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
            setAreaSize(cw + NEI_BORDER, ch + NEI_BORDER);
        } else {
            setAreaSize(BASE_W, BASE_H);
        }
    }

    @Override
    public void draw(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
        ensureRecipeHandler();

        // Widget-local mouse while hovered; parked far away otherwise so NEI's stack hover box
        // only shows on the node actually under the mouse.
        if (getContext().isHovered(this)) {
            hoverMx = canvas.getCanvasMouseX() - Math.round(node.x);
            hoverMy = canvas.getCanvasMouseY() - Math.round(node.y);
        } else {
            hoverMx = -10000;
            hoverMy = -10000;
        }

        if (neiWidget != null && handlerRef != null) {
            final long now = Minecraft.getSystemTime();
            if (now - lastHandlerUpdate > NEI_HANDLER_THROTTLE_MS) {
                lastHandlerUpdate = now;
                handlerRef.handler.onUpdate();
            }

            glPushAttrib(GL_ENABLE_BIT | GL_LIGHTING_BIT | GL_COLOR_BUFFER_BIT);
            glTranslatef(0, 0, Z_PUSH);
            GuiContainerManager.enable2DRender();
            glColor4f(1, 1, 1, 1);

            final int cw = neiWidget.w + NEI_PAD_W;
            final int ch = neiWidget.h + NEI_PAD_H;

            BG_TEXTURE.draw(
                -TEXTURE_OFF,
                -TEXTURE_OFF,
                cw + TEXTURE_EXTRA,
                ch + TEXTURE_EXTRA,
                BORDER_9P,
                BORDER_9P,
                BORDER_9P,
                BORDER_9P);

            glEnable(GL_TEXTURE_2D);
            final int titleCol = PlannhColors.titleColor(recipeName);
            GuiDraw.drawRect(CONTENT_INSET, CONTENT_INSET, cw - TITLE_BAR_RMARGIN, TITLE_BAR_H, titleCol);
            GuiDraw.drawRect(
                CONTENT_INSET,
                CONTENT_TOP,
                cw - TITLE_BAR_RMARGIN,
                TITLE_UL_H,
                PlannhColors.NODE_TITLE_LINE.getColor());
            // The title names the machine the node is modelled as, since that is what its numbers
            // come from, and clicking it picks a different one. Colour still keys on the recipe so a
            // node does not change hue when its machine does.
            final String title = titleText();
            final int titleW = Minecraft.getMinecraft().fontRenderer.getStringWidth(title);
            GuiDraw.drawText(
                title,
                (float) neiWidget.w / 2 - (float) titleW / 2,
                TITLE_TEXT_Y,
                1.0f,
                PlannhColors.textOn(titleCol),
                false);

            // Ask the badge itself rather than "is anything stored": a node whose only stored key
            // is its machine has plenty stored and nothing to badge.
            final String badge = buildConfigBadge();
            if (!badge.isEmpty()) {
                GuiDraw.drawText(badge, LEFT_CONTENT_X, TITLE_TEXT_Y, 1.0f, PlannhColors.TEXT_BADGE.getColor(), false);
            }

            final Group grp = canvas.getGroupForNode(node.id);
            if (grp != null) {
                final int gc = groupColor(grp);
                final String gl = "\u229f " + grp.getHeader();
                final int glW = Minecraft.getMinecraft().fontRenderer.getStringWidth(gl);
                GuiDraw.drawText(gl, cw - glW - GROUP_LABEL_RMARGIN, TITLE_TEXT_Y, 1.0f, gc, false);
            }

            GuiDraw.drawText(
                "\u2699",
                cw - GEAR_X_RMARGIN,
                GEAR_Y,
                1.0f,
                configOpen ? PlannhColors.ACCENT_GREEN.getColor() : PlannhColors.TEXT_DIM.getColor(),
                false);

            // Real mouse position enables NEI's own hover box on recipe stacks.
            neiWidget.draw(hoverMx, hoverMy);
            drawThroughputInfo();
            drawConfigContent();
            repeatHeldZone();
            drawCloseButtonPixel(getArea().width, getArea().height);
            drawPorts();
            drawGroupMembershipBar();
            glPopAttrib();
            glTranslatef(0, 0, Z_POP);
        } else {
            final int w = getArea().width;
            final int h = getArea().height;

            GuiDraw.drawRect(0, 0, w, h, PlannhColors.NODE_BG.getColor());
            GuiHelper.drawRectBorder(0, 0, w, h, 1, PlannhColors.NODE_BORDER.getColor());

            assert node.machineName != null;
            GuiDraw.drawText(
                node.machineName.isEmpty() ? "?" : node.machineName,
                SIMPLE_TEXT_INSET_X,
                SIMPLE_TEXT_INSET_Y,
                1.0f,
                PlannhColors.TEXT_LIGHT.getColor(),
                false);

            final Balancer.NodeBalance simpleNb = getNodeBalance();
            final double simpleOps = simpleNb != null ? simpleNb.operations() : 1;
            final int simpleDurPerOp = simpleNb != null ? simpleNb.durationPerOp() : node.getRecipeDuration();
            final StringBuilder simpleTiming = new StringBuilder();
            if (simpleOps > 0) {
                simpleTiming.append("\u00d7")
                    .append(GuiHelper.formatCount(simpleOps));
            }
            if (simpleDurPerOp > 0) {
                if (!simpleTiming.isEmpty()) simpleTiming.append("  ");
                simpleTiming.append(simpleDurPerOp)
                    .append("t (")
                    .append(String.format("%.1f", (float) simpleDurPerOp / GuiHelper.TICKS_PER_SECOND))
                    .append("s)");
            }
            final String tier = collapsedVoltageTier();
            if (!tier.isEmpty()) {
                simpleTiming.append("  ")
                    .append(tier);
            }
            GuiDraw.drawText(
                simpleTiming.toString(),
                SIMPLE_TEXT_INSET_X,
                h - BOTTOM_TIMING_Y_FROM_BOTTOM,
                1.0f,
                PlannhColors.ACCENT_BLUE.getColor(),
                false);

            final ItemStack primary = getFirstItemOutput();
            if (primary != null) {
                final int is = ICON_SIZE;
                GuiDraw.drawItem(primary, w - is - ICON_RMARGIN, ICON_Y, is, is, context.getCurrentDrawingZ());
            }

            final Group grp2 = canvas.getGroupForNode(node.id);
            if (grp2 != null) {
                GuiDraw.drawText(
                    "\u229f " + grp2.getHeader(),
                    SIMPLE_TEXT_INSET_X,
                    SIMPLE_GROUP_LABEL_Y,
                    1.0f,
                    groupColor(grp2),
                    false);
            }

            drawCloseButtonPixel(w, h);
            drawPorts();
            drawGroupMembershipBar();
        }
    }

    private void drawCloseButtonPixel(final int w, final int h) {
        GuiHelper.drawCloseButton(
            w,
            CLOSE_W,
            CLOSE_MARGIN,
            PlannhColors.BTN_DELETE_BG.getColor(),
            PlannhColors.ACCENT_RED_X.getColor());
    }

    public int getOutputPortAt(final int mx, final int my) {
        final int half = PORT_HALF;
        final int px = getArea().width - PORT_SIZE;
        for (int i = 0; i < node.outputs.size(); i++) {
            final int py = portTopY(i) - half;
            if (mx >= px && mx < px + PORT_SIZE && my >= py && my < py + PORT_SIZE) return i;
        }
        return -1;
    }

    public int getInputPortAt(final int mx, final int my) {
        final int half = PORT_HALF;
        for (int i = 0; i < node.inputs.size(); i++) {
            final int py = portTopY(i) - half;
            if (mx >= 0 && mx < PORT_SIZE && my >= py && my < py + PORT_SIZE) return i;
        }
        return -1;
    }

    private void drawGroupMembershipBar() {
        final Group group = canvas.getGroupForNode(node.id);
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

        for (int i = 0; i < node.outputs.size(); i++) {
            final int py = portTopY(i) - half;
            final int color = node.outputs.get(i)
                .getPinColor(false);
            // Contrast outline keeps the pin visible against any world background.
            GuiDraw.drawRect(getArea().width - ps - 1, py - 1, ps + 2, ps + 2, IngredientColors.outlineFor(color));
            GuiDraw.drawRect(getArea().width - ps, py, ps, ps, color);
        }
        for (int i = 0; i < node.inputs.size(); i++) {
            final int py = portTopY(i) - half;
            final int color = node.inputs.get(i)
                .getPinColor(true);
            GuiDraw.drawRect(-1, py - 1, ps + 2, ps + 2, IngredientColors.outlineFor(color));
            GuiDraw.drawRect(0, py, ps, ps, color);
        }
    }

    private void drawThroughputInfo() {
        if (neiWidget == null) return;

        final int x = LEFT_CONTENT_X;
        int y = CONTENT_TOP + neiWidget.h + THROUGHPUT_GAP;

        final Balancer.NodeBalance nb = getNodeBalance();
        final float sec = nb != null && nb.totalDurationTicks() > 0
            ? (float) nb.totalDurationTicks() / GuiHelper.TICKS_PER_SECOND
            : node.getRecipeDuration() > 0 ? (float) node.getRecipeDuration() / GuiHelper.TICKS_PER_SECOND : 1f;
        final double ops = nb != null ? nb.operations() : 1;

        final int durPerOp = nb != null ? nb.durationPerOp() : node.getRecipeDuration();
        final StringBuilder opsLine = new StringBuilder();
        // No balance (unpinned Auto): show the recipe duration only - no count, and below, no
        // throughput rows. An unpinned chart is wiring, not a solved plan; per-machine rates
        // would be numbers with no anchor.
        if (ops > 0) {
            opsLine.append("\u00d7")
                .append(GuiHelper.formatCount(ops));
        }
        if (durPerOp > 0) {
            if (!opsLine.isEmpty()) opsLine.append("  ");
            opsLine.append(durPerOp)
                .append("t (")
                .append(String.format("%.2f", (float) durPerOp / GuiHelper.TICKS_PER_SECOND))
                .append("s)");
        }
        GuiDraw.drawText(opsLine.toString(), x, y, 1.0f, PlannhColors.ACCENT_BLUE.getColor(), false);
        y += LINE_H;
        if (ops <= 0) return;

        y = drawPortList(x, y, node.inputs, nb, sec, false);
        drawPortList(x, y, node.outputs, nb, sec, true);
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
        // Right-click on a settings row hands that setting back to the machine. Nothing else in the
        // panel can reach that state, so a row nudged and put back would otherwise stay overridden.
        if (mouseButton == 1 && configOpen) {
            final int mx = getContext().getMouseX();
            final int my = getContext().getMouseY();
            for (final ClickZone zone : configZones) {
                if (zone.resetKey() != null && zone.contains(mx, my)) {
                    PlanAPI.recordEdit(canvas.getGraph(), () -> node.machineConfig.clear(zone.resetKey()));
                    onConfigChanged();
                    return Result.SUCCESS;
                }
            }
        }
        if (mouseButton == 0) {
            final int mx = getContext().getMouseX();
            final int my = getContext().getMouseY();
            if (GuiHelper.isInsideCloseButton(mx, my, getArea().width, CLOSE_W, CLOSE_MARGIN)) {
                canvas.removeNode(node.id);
                return Result.SUCCESS;
            }

            if (neiWidget != null) {
                final int cw = neiWidget.w + NEI_PAD_W;
                // Left of the gear along the title bar: pick which machine runs this recipe.
                if (mx >= CONTENT_INSET && mx < cw - GEAR_HIT_LEFT_OFF
                    && my >= CONTENT_INSET
                    && my < CONTENT_INSET + TITLE_BAR_H
                    && hasMachineChoice()) {
                    canvas.openMachinePicker(node);
                    return Result.SUCCESS;
                }
                if (mx >= cw - GEAR_HIT_LEFT_OFF && mx <= cw - GEAR_HIT_RIGHT_OFF
                    && my >= GEAR_HIT_TOP
                    && my <= GEAR_HIT_BOTTOM) {
                    configOpen = !configOpen;
                    resizeForZoom(
                        canvas.getGraph()
                            .getZoom());
                    return Result.SUCCESS;
                }
                if (configOpen) {
                    for (final ClickZone zone : configZones) {
                        if (zone.contains(mx, my)) {
                            // Held repeats mutate outside this bracket and fold into this entry.
                            PlanAPI.recordEdit(canvas.getGraph(), zone.action);
                            if (zone.repeat()) {
                                zoneHeld = true;
                                heldZoneMx = mx;
                                heldZoneMy = my;
                                zoneHoldStart = System.currentTimeMillis();
                                zoneLastRepeat = zoneHoldStart;
                            }
                            return Result.SUCCESS;
                        }
                    }
                }
            }

            if (getOutputPortAt(mx, my) >= 0) return Result.IGNORE;

            if (doubleClick.check()) {
                doubleClickPending = true;
                return Result.SUCCESS;
            }
            doubleClickPending = false;
            dragging = true;
            dragEditToken = PlanAPI.undoHistory()
                .beginEdit(canvas.getGraph());
            dragStartMouseX = getContext().getAbsMouseX();
            dragStartMouseY = getContext().getAbsMouseY();
            nodeStartX = node.x;
            nodeStartY = node.y;
            return Result.SUCCESS;
        }
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
            node.x = nodeStartX + Math.round(dx / z);
            node.y = nodeStartY + Math.round(dy / z);
            canvas.clampNodeToGroup(node);
            syncTransform(
                z,
                canvas.getGraph()
                    .getPanX(),
                canvas.getGraph()
                    .getPanY());
        }
    }

    /** The machine name when the node has one to offer, otherwise the recipe's own name. */
    private String titleText() {
        final SettingDef<?> def = node.machineConfig.getProfile()
            .setting(GTSettings.MACHINE);
        if (def == null) return recipeName;
        final List<String> options = def.options(recipeContext());
        if (options.isEmpty()) return recipeName;
        final String stored = node.machineConfig.getString(def.key);
        return def.display(options.contains(stored) ? stored : options.getFirst());
    }

    private boolean hasMachineChoice() {
        final SettingDef<?> def = node.machineConfig.getProfile()
            .setting(GTSettings.MACHINE);
        return def != null && def.options(recipeContext())
            .size() > 1;
    }

    /**
     * The tier the collapsed node runs at. Reading the stored key directly showed nothing for every
     * node that never picked one, which under a sparse map is most of them.
     */
    private String collapsedVoltageTier() {
        final SettingDef<?> def = node.machineConfig.getProfile()
            .setting(Settings.VOLTAGE.key());
        if (def == null) return "";
        final List<String> options = def.options(recipeContext());
        if (options.isEmpty()) return "";
        final String stored = node.machineConfig.getString(def.key);
        return options.contains(stored) ? stored : options.getFirst();
    }

    private RecipeContext recipeContext() {
        return new RecipeContext(node.properties);
    }

    /** The rows this node renders; a setting the profile hides must not badge or size the panel. */
    private List<SettingDef<?>> visibleSettings() {
        final MachineConfig c = node.machineConfig;
        return c.getProfile()
            .visibleSettings(recipeContext(), c.settings);
    }

    private String buildConfigBadge() {
        final MachineConfig c = node.machineConfig;
        final StringBuilder sb = new StringBuilder();

        for (final SettingDef<?> def : visibleSettings()) {
            // Presence is the choice now, so a value deliberately set back to the declared default
            // still counts - it is the machine's value being overridden, not an untouched row.
            final Object val = c.settings.get(def.key);
            if (val == null) continue;
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
        final int panelH = configRowsHeight() + 4;
        GuiDraw.drawRect(
            x - CONFIG_PANEL_INSET,
            y0 - CONFIG_PANEL_INSET,
            CONFIG_PANEL_W,
            panelH,
            PlannhColors.SETTINGS_PANEL_BG.getColor());

        final MachineConfig c = node.machineConfig;
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

        for (final SettingDef<?> def : visibleSettings()) {
            y = drawSetting(x, y, def, c);
        }

        // One row per output: pin the rate the chart should produce. The row opens a text
        // editor; rates are typed, not stepped.
        final FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        for (final int idx : targetableOutputs()) {
            final double current = node.targetOutputRates.getOrDefault(idx, 0.0);
            // Scale suffixes but not the port's formatter: this row is what the rate editor writes
            // back, and the editor takes a plain number. "1.2k" is the same quantity as 1200, but a
            // fluid's "1.0B" is 1000 litres in a field that wants litres.
            final String value = current > 0 ? GuiHelper.formatRate((float) current) + "/s" : "off";
            final int valueW = font.getStringWidth(value);
            final String label = font.trimStringToWidth(
                "Tgt " + node.outputs.get(idx)
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

    /**
     * Draws a row and tags every zone it produced with the setting it edits, so a right-click
     * anywhere on the row resets it. Tagging here rather than at each {@code configZones.add} keeps
     * the three row shapes - stepper, checkbox, list - from each having to know about resetting.
     */
    private int drawSetting(final int x, final int y, final SettingDef<?> def, final MachineConfig c) {
        final int firstZone = configZones.size();
        final int next = drawSettingRow(x, y, def, c);
        for (int i = firstZone; i < configZones.size(); i++) {
            configZones.set(
                i,
                configZones.get(i)
                    .withReset(def.key));
        }
        return next;
    }

    private int drawSettingRow(final int x, final int y, final SettingDef<?> def, final MachineConfig c) {
        if (def.type == Integer.class) {
            // An auto setting shows what the machine actually does rather than the 0 that means
            // "ask the machine", and cannot be stepped past what that machine allows.
            final int shown = def.isAuto() ? def.effectiveInt(recipeContext(), c.settings) : c.getInt(def.key);
            // Always asked for: a row can have a machine-set ceiling without being an auto row, and
            // effectiveMax falls back to the declared maximum when it has neither.
            final int max = def.effectiveMax(recipeContext(), c.settings);
            return drawConfigIntField(x, y, def.label, shown, def.minInt, max, v -> {
                c.setInt(def.key, v);
                onConfigChanged();
            });
        } else if (def.type == Boolean.class) {
            final boolean val = def.isAuto() ? def.effectiveBool(recipeContext(), c.settings) : c.getBoolean(def.key);
            final String label = (val ? "[\u2713] " : "[  ] ") + def.label;
            GuiDraw.drawText(
                label,
                x,
                y,
                1.0f,
                val ? PlannhColors.SETTING_ON.getColor() : PlannhColors.SETTING_OFF.getColor(),
                false);
            configZones.add(new ClickZone(x, y, x + BOOL_CLICK_W, y + CLICK_H, () -> {
                c.setBoolean(def.key, !val);
                onConfigChanged();
            }));
            return y + LINE_H;
        } else if (def.type == String.class && def.hasOptions()) {
            final List<String> options = def.options(recipeContext());
            if (options.isEmpty()) return y;

            final String stored = c.getString(def.key);
            final int cur = options.indexOf(stored);
            // Nothing stored resolves to the first option, so a node that accepts the obvious
            // choice serializes nothing. A stored value the list no longer offers is a machine the
            // pack removed: show it flagged rather than silently rewriting the user's chart.
            final boolean missing = cur < 0 && !stored.isEmpty();
            final String shown = cur >= 0 || missing ? stored : options.getFirst();
            final int color = missing ? PlannhColors.ACCENT_RED_X.getColor()
                : cur < 0 ? PlannhColors.TEXT_MUTED.getColor() : PlannhColors.SETTING_ON.getColor();
            // Machine names run far longer than a tier abbreviation, and the steppers sit at a fixed
            // offset, so an untrimmed row draws straight through them.
            final String row = def.label + " " + def.display(shown) + (missing ? " ?" : "");
            GuiDraw.drawText(
                Minecraft.getMinecraft().fontRenderer.trimStringToWidth(row, SETTING_DEC_X - SETTING_LABEL_GAP),
                x,
                y,
                1.0f,
                color,
                false);

            final int decX = x + SETTING_DEC_X;
            final int incX = decX + SETTING_BTN_W;
            GuiDraw.drawText("[-]", decX, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);
            GuiDraw.drawText("[+]", incX, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);

            configZones.add(new ClickZone(decX, y, incX, y + CLICK_H, () -> cycleOption(def, c, -1), true));
            configZones
                .add(new ClickZone(incX, y, incX + SETTING_BTN_W, y + CLICK_H, () -> cycleOption(def, c, +1), true));
            return y + LINE_H;
        }
        return y + LINE_H;
    }

    /** Steps an enum row. An unset or unknown value lands on the first option, never off the end. */
    private void cycleOption(final SettingDef<?> def, final MachineConfig c, final int step) {
        final List<String> options = def.options(recipeContext());
        if (options.isEmpty()) return;
        // An unset row displays the first option, so stepping starts from there. Treating unset as
        // "no index" instead made the first click rewrite the value already on screen.
        final int shown = Math.max(0, options.indexOf(c.getString(def.key)));
        c.setString(def.key, options.get(Math.min(options.size() - 1, Math.max(0, shown + step))));
        onConfigChanged();
    }

    private int computeConfigPanelHeight() {
        return configOpen ? configRowsHeight() + 8 : 0;
    }

    private int configRowsHeight() {
        int h = (visibleSettings().size() + 2 + targetableOutputs().size()) * LINE_H;
        if (node.getAvailableExtractors()
            .size() > 1) h += LINE_H;
        return h;
    }

    /** Output indices that get a target row: the same ports the throughput list shows. */
    private List<Integer> targetableOutputs() {
        final List<Integer> result = new ArrayList<>();
        for (int i = 0; i < node.outputs.size(); i++) {
            if (hasVisibleAmount(node.outputs.get(i))) {
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
        canvas.onNodeConfigChanged(node);
    }

    @Nullable
    private ItemStack getFirstItemOutput() {
        for (final Port<?> port : node.outputs) {
            if (port.getType() == RecipePropertyAPI.ITEM) return (ItemStack) port.getValue();
        }
        return null;
    }

    private void openNeiRecipe() {
        for (final Port<?> port : node.outputs) {
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
        final int mx = canvas.getCanvasMouseX() - Math.round(node.x);
        final int my = canvas.getCanvasMouseY() - Math.round(node.y);

        if (neiWidget != null && handlerRef != null) {
            final PositionedStack gridStack = neiWidget.getPositionedStackMouseOver(mx, my);
            if (gridStack != null) {
                return new IngredientHit(gridStack.item, portOriginFor(gridStack.item, gridSlotIsInput(gridStack)));
            }
        }

        final int out = getOutputPortAt(mx, my);
        if (out >= 0) return hitFor(node.outputs.get(out), true, out);
        final int in = getInputPortAt(mx, my);
        if (in >= 0) return hitFor(node.inputs.get(in), false, in);
        return null;
    }

    @Nullable
    private IngredientHit hitFor(final Port<?> port, final boolean output, final int index) {
        final ItemStack stack = port.getDisplayStack();
        if (stack == null) return null;
        return new IngredientHit(stack, new NodeLookupContext(node.id, output, index));
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
        final List<Port<?>> ports = output ? node.outputs : node.inputs;
        for (int i = 0; i < ports.size(); i++) {
            if (displayMatches(ports.get(i), stack)) return new NodeLookupContext(node.id, output, i);
        }
        return null;
    }

    private static boolean displayMatches(final Port<?> port, final ItemStack stack) {
        final ItemStack display = port.getDisplayStack();
        return display != null && display.isItemEqual(stack);
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
