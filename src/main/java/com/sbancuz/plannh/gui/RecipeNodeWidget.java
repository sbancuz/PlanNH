package com.sbancuz.plannh.gui;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.Balancer.NodeBalance;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.nei.NodeLookupContext;

import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.NEIRecipeWidget;
import codechicken.nei.recipe.RecipeHandlerRef;
import lombok.Getter;

public class RecipeNodeWidget extends Widget<RecipeNodeWidget> implements Interactable, RecipeViewerIngredientProvider {

    private static final int BASE_W = 120;
    private static final int BASE_H = 80;
    private static final int CLOSE_W = 12;
    private static final int CLOSE_MARGIN = 2;
    private static final int PORT_SIZE = 8;
    private static final int PORT_HALF = PORT_SIZE / 2;
    private static final int PORT_SPACING = 18;
    private static final int PORT_ORIGIN = 10;
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

    private record ClickZone(int ux1, int uy1, int ux2, int uy2, Runnable action) {

        boolean contains(final int ux, final int uy) {
            return ux >= ux1 && ux < ux2 && uy >= uy1 && uy < uy2;
        }
    }

    public RecipeNodeWidget(final Node node, final CanvasWidget canvas) {
        this.node = node;
        this.canvas = canvas;
        size(BASE_W, BASE_H);
    }

    int getWorldWidth() {
        if (handlerRef != null && neiWidget != null) {
            return neiWidget.w + NEI_PAD_W + NEI_BORDER;
        }
        return BASE_W;
    }

    int getWorldHeight() {
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
    private NodeBalance getNodeBalance() {
        final BalanceResult br = canvas.getGraph()
            .balance();
        return br.nodeBalances()
            .get(node.id);
    }

    private int calcInfoHeight() {
        final int lines = 1 + node.inputs.size() + node.outputs.size();
        return lines * LINE_H + 6;
    }

    private void ensureRecipeHandler() {
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
            final int cw = getWorldWidth() - NEI_BORDER;
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
            hoverMx = canvas.getMouseCanvasX() - Math.round(node.x);
            hoverMy = canvas.getMouseCanvasY() - Math.round(node.y);
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

            BG_TEXTURE.draw(-TEXTURE_OFF, -TEXTURE_OFF, cw + TEXTURE_EXTRA, ch + TEXTURE_EXTRA, BORDER_9P, BORDER_9P, BORDER_9P, BORDER_9P);

            glEnable(GL_TEXTURE_2D);
            final int titleCol = PlannhColors.titleColor(recipeName);
            GuiDraw.drawRect(CONTENT_INSET, CONTENT_INSET, cw - TITLE_BAR_RMARGIN, TITLE_BAR_H, titleCol);
            GuiDraw.drawRect(CONTENT_INSET, CONTENT_TOP, cw - TITLE_BAR_RMARGIN, TITLE_UL_H, PlannhColors.NODE_TITLE_LINE.getColor());
            final int titleW = Minecraft.getMinecraft().fontRenderer.getStringWidth(recipeName);
            GuiDraw.drawText(
                recipeName,
                (float) neiWidget.w / 2 - (float) titleW / 2,
                TITLE_TEXT_Y,
                1.0f,
                PlannhColors.textOn(titleCol),
                false);

            if (node.machineConfig.hasAnyBoost()) {
                GuiDraw.drawText(buildConfigBadge(), LEFT_CONTENT_X, TITLE_TEXT_Y, 1.0f, PlannhColors.TEXT_BADGE.getColor(), false);
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

            // Passing the real mouse position enables NEI's own hover box on recipe stacks,
            // signaling that the R/U keybinds work there.
            neiWidget.draw(hoverMx, hoverMy);
            drawThroughputInfo();
            drawConfigContent();
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

            final NodeBalance simpleNb = getNodeBalance();
            final int simpleOps = simpleNb != null ? simpleNb.operations() : 1;
            final int simpleDurPerOp = simpleNb != null ? simpleNb.durationPerOp() : node.durationTicks;
            final StringBuilder simpleTiming = new StringBuilder();
            simpleTiming.append("\u00d7").append(simpleOps);
            if (simpleDurPerOp > 0) {
                simpleTiming.append("  ")
                    .append(simpleDurPerOp)
                    .append("t (")
                    .append(String.format("%.1f", (float) simpleDurPerOp / GuiHelper.TICKS_PER_SECOND))
                    .append("s)");
            }
            final Object rawVoltage = node.machineConfig.settings.get("voltage");
            if (rawVoltage instanceof final String v && !"OFF".equals(v)) {
                simpleTiming.append("  ").append(v);
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
                GuiDraw.drawText("\u229f " + grp2.getHeader(), SIMPLE_TEXT_INSET_X, SIMPLE_GROUP_LABEL_Y, 1.0f, groupColor(grp2), false);
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
        return (index + 1) * PORT_SPACING + PORT_ORIGIN;
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

        final NodeBalance nb = getNodeBalance();
        final float sec = nb != null && nb.totalDurationTicks() > 0
            ? (float) nb.totalDurationTicks() / GuiHelper.TICKS_PER_SECOND
            : node.durationTicks > 0 ? (float) node.durationTicks / GuiHelper.TICKS_PER_SECOND : 1f;
        final int ops = nb != null ? nb.operations() : 1;
        final int throughput = nb != null ? node.machineConfig.computeEffect(node.properties, node.durationTicks)
            .throughputFactor() : 1;

        final int durPerOp = nb != null ? nb.durationPerOp() : node.durationTicks;
        final StringBuilder opsLine = new StringBuilder();
        opsLine.append("\u00d7")
            .append(ops);
        if (durPerOp > 0) {
            opsLine.append("  ")
                .append(durPerOp)
                .append("t (")
                .append(String.format("%.2f", (float) durPerOp / GuiHelper.TICKS_PER_SECOND))
                .append("s)");
        }
        GuiDraw.drawText(opsLine.toString(), x, y, 1.0f, PlannhColors.ACCENT_BLUE.getColor(), false);
        y += LINE_H;

        y = drawPortList(x, y, node.inputs, nb, sec, ops, throughput, false);
        drawPortList(x, y, node.outputs, nb, sec, ops, throughput, true);
    }

    private int drawPortList(final int x, int y, final List<Port<?>> ports, final NodeBalance nb, final float sec,
        final int ops, final int throughput, final boolean output) {
        for (int i = 0; i < ports.size(); i++) {
            final Port<?> port = ports.get(i);
            final String label = portLabel(port, i, nb, sec, ops, throughput, output);
            if (label == null) continue;
            GuiDraw.drawText(label, x + (output ? LIST_INDENT : 0), y, 1.0f, portColor(port, output), false);
            y += LINE_H;
        }
        return y;
    }

    @Nullable
    private String portLabel(final Port<?> port, final int index, final NodeBalance nb, final float sec, final int ops,
        final int throughput, final boolean output) {
        if (!hasVisibleAmount(port)) return null;
        if (port.getType() == RecipePropertyAPI.ITEM) {
            final ItemStack stack = (ItemStack) port.getValue();
            final float total = (output ? nb.effectiveOutputs() : nb.effectiveInputs()).containsKey(index) ? output
                ? nb.effectiveOutputs()
                    .get(index)
                : nb.effectiveInputs()
                    .get(index)
                : stack.stackSize;
            String label = formatRate(total / sec) + "/s " + stack.getDisplayName();
            if (output && port.getChance() < 0.999f) {
                label += " (" + Math.round(port.getChance() * 100) + "%)";
            }
            return label;
        }
        if (port.getType() == RecipePropertyAPI.FLUID) {
            final FluidStack fs = (FluidStack) port.getValue();
            final float total;
            if (output) {
                total = ops * (float) fs.amount * port.getChance() * throughput;
            } else {
                total = nb.effectiveInputs()
                    .containsKey(index)
                        ? nb.effectiveInputs()
                            .get(index)
                        : (float) fs.amount;
            }
            return formatRate(total / sec) + "/s " + fs.getLocalizedName();
        }
        return null;
    }

    private static int portColor(final Port<?> port, final boolean output) {
        if (port.getType() == RecipePropertyAPI.FLUID) {
            return output ? PlannhColors.ACCENT_CYAN.getColor() : PlannhColors.ACCENT_BLUE3.getColor();
        }
        return output ? PlannhColors.ACCENT_YELLOW.getColor() : PlannhColors.TEXT_MUTED.getColor();
    }

    private static String formatRate(final float rate) {
        if (rate >= 1000000) return String.format("%.1fM", rate / 1000000);
        if (rate >= 1000) return String.format("%.0f", rate);
        if (rate >= 1) return String.format("%.2f", rate);
        return String.format("%.3f", rate);
    }

    @Override
    public @Nonnull Result onMousePressed(final int mouseButton) {
        if (mouseButton == 0) {
            final int mx = getContext().getMouseX();
            final int my = getContext().getMouseY();
            if (GuiHelper.isInsideCloseButton(mx, my, getArea().width, CLOSE_W, CLOSE_MARGIN)) {
                canvas.removeNode(node.id);
                return Result.SUCCESS;
            }

            if (neiWidget != null) {
                final int cw = neiWidget.w + NEI_PAD_W;
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
                            zone.action.run();
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
            if (doubleClickPending) {
                doubleClickPending = false;
                openNeiRecipe();
                return true;
            }
            dragging = false;
            canvas.recheckMembershipAndFit();
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

    private String buildConfigBadge() {
        final MachineConfig c = node.machineConfig;
        final MachineProfile profile = c.getProfile();
        final StringBuilder sb = new StringBuilder();

        for (final SettingDef<?> def : profile.settings()) {
            final Object val = c.settings.get(def.key);
            if (val == null) continue;
            if (val.equals(def.defaultValue)) continue;
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
        final MachineProfile profile = node.machineConfig.getProfile();
        int panelH = (profile.settings()
            .size() + 2) * LINE_H + 4;
        if (node.getAvailableExtractors()
            .size() > 1) panelH += LINE_H;
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

        for (final SettingDef<?> def : profile.settings()) {
            y = drawSetting(x, y, def, c);
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

    private int drawSetting(final int x, final int y, final SettingDef<?> def, final MachineConfig c) {
        if (def.type == Integer.class) {
            return drawConfigIntField(x, y, def.label, c.getInt(def.key), def.minInt, def.maxInt, v -> {
                c.setInt(def.key, v);
                onConfigChanged();
            });
        } else if (def.type == Boolean.class) {
            final boolean val = c.getBoolean(def.key);
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
            final String val = c.getString(def.key);
            GuiDraw.drawText(def.label + " " + val, x, y, 1.0f, PlannhColors.SETTING_ON.getColor(), false);

            final int decX = x + SETTING_DEC_X;
            final int incX = decX + SETTING_BTN_W;
            GuiDraw.drawText("[-]", decX, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);
            GuiDraw.drawText("[+]", incX, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);

            assert def.options != null;

            configZones.add(new ClickZone(decX, y, incX, y + CLICK_H, () -> {
                final int cur = def.options.indexOf(c.getString(def.key));
                c.setString(def.key, def.options.get(Math.max(0, (Math.max(cur, 0)) - 1)));
                onConfigChanged();
            }));
            configZones.add(new ClickZone(incX, y, incX + SETTING_BTN_W, y + CLICK_H, () -> {
                final int cur = def.options.indexOf(c.getString(def.key));
                c.setString(def.key, def.options.get(Math.min(def.options.size() - 1, (Math.max(cur, 0)) + 1)));
                onConfigChanged();
            }));
            return y + LINE_H;
        }
        return y + LINE_H;
    }

    private int computeConfigPanelHeight() {
        if (!configOpen) return 0;
        final MachineProfile profile = node.machineConfig.getProfile();
        int h = (profile.settings()
            .size() + 2) * LINE_H + 8;
        if (node.getAvailableExtractors()
            .size() > 1) h += LINE_H;
        return h;
    }

    private int drawConfigIntField(final int x, final int y, final String label, final int value, final int min,
        final int max, final java.util.function.IntConsumer setter) {
        GuiDraw.drawText(label + " " + value, x, y, 1.0f, PlannhColors.TEXT_LIGHT.getColor(), false);
        GuiDraw.drawText("[-]", x + SETTING_DEC_X, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);
        GuiDraw.drawText("[+]", x + SETTING_INC_X, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);

        configZones.add(
            new ClickZone(
                x + SETTING_DEC_X,
                y,
                x + SETTING_INC_X,
                y + CLICK_H,
                () -> { if (value > min) setter.accept(value - 1); }));
        configZones.add(
            new ClickZone(
                x + SETTING_INC_X,
                y,
                x + SETTING_INC_X + SETTING_BTN_W,
                y + CLICK_H,
                () -> { if (value < max) setter.accept(value + 1); }));
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
     * Tells NEI what ingredient the mouse is over, which enables its native recipe/usage
     * lookups (R/U and bookmark keys) on node contents: the embedded recipe's stacks and the
     * port pins. Also arms the pending-lookup origin so a recipe added from the upcoming
     * lookup can be wired back to the originating port; a stack that maps to no port clears
     * any stale origin.
     */
    @Override
    @Nullable
    public ItemStack getStackForRecipeViewer() {
        final IngredientHit hit = ingredientUnderMouse();
        if (hit == null) return null;
        canvas.setPendingLookup(hit.origin());
        return hit.stack();
    }

    /**
     * The ingredient stack under the mouse, if any. Pure query, safe to call every frame (the
     * screen's hover tooltip does); {@link #getStackForRecipeViewer} is NEI's lookup entry
     * point and additionally arms the pending-lookup origin.
     */
    @Nullable
    public ItemStack stackUnderMouse() {
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
        final int mx = canvas.getMouseCanvasX() - Math.round(node.x);
        final int my = canvas.getMouseCanvasY() - Math.round(node.y);

        if (neiWidget != null && handlerRef != null) {
            final ItemStack gridStack = neiWidget.getStackMouseOver(mx, my);
            if (gridStack != null) return new IngredientHit(gridStack, portOriginFor(gridStack));
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

    /**
     * Which port an embedded recipe-grid stack refers to, by forward display-stack comparison
     * (GT fluid display items encode their fluid in the damage value, so fluid ports need no
     * reverse mapping). Inputs first: for recipes carrying an ingredient on both sides, the
     * dominant workflow is R on an input ("how do I make this"). Null for stacks that are no
     * port's ingredient.
     */
    @Nullable
    private NodeLookupContext portOriginFor(final ItemStack gridStack) {
        for (int i = 0; i < node.inputs.size(); i++) {
            if (displayMatches(node.inputs.get(i), gridStack)) return new NodeLookupContext(node.id, false, i);
        }
        for (int i = 0; i < node.outputs.size(); i++) {
            if (displayMatches(node.outputs.get(i), gridStack)) return new NodeLookupContext(node.id, true, i);
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
