package com.sbancuz.plannh.gui;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceResult;
import com.sbancuz.plannh.data.flowchart.Balancer.NodeBalance;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Node;

import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.NEIRecipeWidget;
import codechicken.nei.recipe.RecipeHandlerRef;
import lombok.Getter;

public class RecipeNodeWidget extends Widget<RecipeNodeWidget> implements Interactable {

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

    private record ClickZone(int ux1, int uy1, int ux2, int uy2, Runnable action) {

        boolean contains(final int ux, final int uy) {
            return ux >= ux1 && ux < ux2 && uy >= uy1 && uy < uy2;
        }
    }

    public RecipeNodeWidget(final Node node, final CanvasWidget canvas) {
        this.node = node;
        this.canvas = canvas;
        final float z = canvas.getZoom();
        size(Math.round(BASE_W * z), Math.round(BASE_H * z));
    }

    public void syncTransform(final float zoom, final float panX, final float panY) {
        final int sx = Math.round(node.x * zoom + panX);
        final int sy = Math.round(node.y * zoom + panY);
        pos(sx, sy);
        resizeForZoom(zoom);
    }

    private int zq(final float v) {
        return GuiHelper.zq(v, canvas.getZoom());
    }

    private int groupColor(final Group g) {
        return g.colorOverride != 0 ? g.colorOverride : PlannhColors.titleColor(g.title);
    }

    @Nullable
    private NodeBalance getNodeBalance() {
        final BalanceResult br = canvas.getGraph()
            .balance();
        return br.nodeBalances()
            .get(node.id);
    }

    private int calcInfoHeight() {
        final int lines = 1 + node.inputs.size()
            + node.outputs.size()
            + node.fluidInputs.size()
            + node.fluidOutputs.size();
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
        resizeForZoom(canvas.getZoom());
    }

    private void setAreaSize(final int pw, final int ph) {
        getArea().width = pw;
        getArea().height = ph;
        size(pw, ph);
    }

    private void resizeForZoom(final float z) {
        if (handlerRef != null && neiWidget != null) {
            final int cw = neiWidget.w + NEI_PAD_W;
            final int ch = neiWidget.h + NEI_PAD_H + calcInfoHeight() + computeConfigPanelHeight();
            setAreaSize(Math.round((cw + NEI_BORDER) * z), Math.round((ch + NEI_BORDER) * z));
        } else {
            setAreaSize(Math.round(BASE_W * z), Math.round(BASE_H * z));
        }
    }

    @Override
    public void draw(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
        ensureRecipeHandler();

        final float z = canvas.getZoom();
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

            glPushMatrix();
            glScalef(z, z, 1);

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
                PlannhColors.TEXT_WHITE.getColor(),
                false);

            if (node.machineConfig.hasAnyBoost()) {
                GuiDraw.drawText(buildConfigBadge(), LEFT_CONTENT_X, TITLE_TEXT_Y, 1.0f, PlannhColors.TEXT_BADGE.getColor(), false);
            }

            final Group grp = canvas.getGroupForNode(node.id);
            if (grp != null) {
                final int gc = groupColor(grp);
                final String gl = "\u229f " + grp.title;
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

            neiWidget.draw(CONTENT_INSET, CONTENT_TOP);
            drawThroughputInfo();
            drawConfigContent();

            glPopMatrix();
            drawCloseButtonPixel(getArea().width, getArea().height);
            drawPorts();
            drawGroupMembershipBar();
            glPopAttrib();
            glTranslatef(0, 0, Z_POP);
        } else {
            final int w = getArea().width;
            final int h = getArea().height;

            GuiDraw.drawRect(0, 0, w, h, PlannhColors.NODE_BG.getColor());
            GuiHelper.drawRectBorder(0, 0, w, h, zq(1), PlannhColors.NODE_BORDER.getColor());

            assert node.machineName != null;
            GuiDraw.drawText(
                node.machineName.isEmpty() ? "?" : node.machineName,
                zq(SIMPLE_TEXT_INSET_X),
                zq(SIMPLE_TEXT_INSET_Y),
                z,
                PlannhColors.TEXT_LIGHT.getColor(),
                false);

            final NodeBalance simpleNb = getNodeBalance();
            final int simpleOps = simpleNb != null ? simpleNb.operations : 1;
            final int simpleDurPerOp = simpleNb != null ? simpleNb.durationPerOp : node.durationTicks;
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
                zq(SIMPLE_TEXT_INSET_X),
                h - zq(BOTTOM_TIMING_Y_FROM_BOTTOM),
                z,
                PlannhColors.ACCENT_BLUE.getColor(),
                false);

            if (!node.outputs.isEmpty()) {
                final ItemStack primary = node.outputs.getFirst()
                    .left();
                if (primary != null) {
                    final int is = zq(ICON_SIZE);
                    GuiDraw.drawItem(primary, w - is - zq(ICON_RMARGIN), zq(ICON_Y), is, is, context.getCurrentDrawingZ());
                }
            }

            final Group grp2 = canvas.getGroupForNode(node.id);
            if (grp2 != null) {
                GuiDraw.drawText("\u229f " + grp2.title, zq(SIMPLE_TEXT_INSET_X), zq(SIMPLE_GROUP_LABEL_Y), z, groupColor(grp2), false);
            }

            drawCloseButtonPixel(w, h);
            drawPorts();
            drawGroupMembershipBar();
        }
    }

    private void drawCloseButtonPixel(final int w, final int h) {
        GuiHelper.drawCloseButton(
            canvas.getZoom(),
            w,
            CLOSE_W,
            CLOSE_MARGIN,
            PlannhColors.BTN_DELETE_BG.getColor(),
            PlannhColors.ACCENT_RED_X.getColor());
    }

    public int getOutputPortAt(final int mx, final int my) {
        final int half = zq(PORT_HALF);
        final int px = getArea().width - zq(PORT_SIZE);
        for (int i = 0; i < node.outputs.size(); i++) {
            final int py = portTopY(i, 0) - half;
            if (mx >= px && mx < px + zq(PORT_SIZE) && my >= py && my < py + zq(PORT_SIZE)) return i;
        }
        for (int i = 0; i < node.fluidOutputs.size(); i++) {
            final int py = portTopY(i, node.outputs.size()) - half;
            if (mx >= px && mx < px + zq(PORT_SIZE) && my >= py && my < py + zq(PORT_SIZE)) return i;
        }
        return -1;
    }

    public int getInputPortAt(final int mx, final int my) {
        final int half = zq(PORT_HALF);
        for (int i = 0; i < node.inputs.size(); i++) {
            final int py = portTopY(i, 0) - half;
            if (mx >= 0 && mx < zq(PORT_SIZE) && my >= py && my < py + zq(PORT_SIZE)) return i;
        }
        for (int i = 0; i < node.fluidInputs.size(); i++) {
            final int py = portTopY(i, node.inputs.size()) - half;
            if (mx >= 0 && mx < zq(PORT_SIZE) && my >= py && my < py + zq(PORT_SIZE)) return i;
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
            zq(GROUP_BAR_W),
            getArea().height,
            Color.argb(Color.getRed(gc), Color.getGreen(gc), Color.getBlue(gc), ALPHA_BAR));
    }

    private int portTopY(final int i, final int totalBefore) {
        final float z = canvas.getZoom();
        return Math.round(((totalBefore + i + 1) * PORT_SPACING + PORT_ORIGIN) * z);
    }

    private void drawPorts() {
        final int ps = zq(PORT_SIZE);
        final int half = zq(PORT_HALF);

        for (int i = 0; i < node.outputs.size(); i++) {
            final int py = portTopY(i, 0) - half;
            GuiDraw
                .drawRect(getArea().width - ps - 1, py - 1, ps + 2, ps + 2, PlannhColors.PIN_OUTPUT_HOVER.getColor());
            GuiDraw.drawRect(getArea().width - ps, py, ps, ps, PlannhColors.PIN_OUTPUT.getColor());
        }
        for (int i = 0; i < node.inputs.size(); i++) {
            final int py = portTopY(i, 0) - half;
            GuiDraw.drawRect(-1, py - 1, ps + 2, ps + 2, PlannhColors.PIN_INPUT_HOVER.getColor());
            GuiDraw.drawRect(0, py, ps, ps, PlannhColors.PIN_INPUT.getColor());
        }
        for (int i = 0; i < node.fluidOutputs.size(); i++) {
            final int py = portTopY(i, node.outputs.size()) - half;
            GuiDraw.drawRect(getArea().width - ps - 1, py - 1, ps + 2, ps + 2, PlannhColors.PIN_FLUID_OUT_H.getColor());
            GuiDraw.drawRect(getArea().width - ps, py, ps, ps, PlannhColors.PIN_FLUID_OUT.getColor());
        }
        for (int i = 0; i < node.fluidInputs.size(); i++) {
            final int py = portTopY(i, node.inputs.size()) - half;
            GuiDraw.drawRect(-1, py - 1, ps + 2, ps + 2, PlannhColors.PIN_FLUID_IN_H.getColor());
            GuiDraw.drawRect(0, py, ps, ps, PlannhColors.PIN_FLUID_IN.getColor());
        }
    }

    private void drawThroughputInfo() {
        if (neiWidget == null) return;

        final int x = LEFT_CONTENT_X;
        int y = CONTENT_TOP + neiWidget.h + THROUGHPUT_GAP;

        final NodeBalance nb = getNodeBalance();
        final float sec = nb != null && nb.totalDurationTicks > 0
            ? (float) nb.totalDurationTicks / GuiHelper.TICKS_PER_SECOND
            : node.durationTicks > 0 ? (float) node.durationTicks / GuiHelper.TICKS_PER_SECOND : 1f;
        final int ops = nb != null ? nb.operations : 1;
        final int throughput = nb != null
            ? node.machineConfig.computeEffect(node.properties.asMap(), node.durationTicks)
                .throughputFactor()
            : 1;

        final int durPerOp = nb != null ? nb.durationPerOp : node.durationTicks;
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

        y = drawIOList(x, y, node.inputs, i -> {
            final var pair = node.inputs.get(i);
            if (pair.left() == null) return null;
            final float total = nb != null && nb.effectiveInputs.containsKey(i) ? nb.effectiveInputs.get(i)
                : pair.left().stackSize;
            return formatRate(total / sec) + "/s "
                + pair.left()
                    .getDisplayName();
        }, PlannhColors.TEXT_MUTED.getColor(), false);

        y = drawIOList(x, y, node.outputs, i -> {
            final var pair = node.outputs.get(i);
            if (pair.left() == null) return null;
            final float total = nb != null && nb.effectiveOutputs.containsKey(i) ? nb.effectiveOutputs.get(i)
                : pair.left().stackSize;
            String label = formatRate(total / sec) + "/s "
                + pair.left()
                    .getDisplayName();
            final float chance = pair.rightFloat();
            if (chance < 0.999f) label += " (" + Math.round(chance * 100) + "%)";
            return label;
        }, PlannhColors.ACCENT_YELLOW.getColor(), true);

        y = drawIOList(x, y, node.fluidInputs, i -> {
            final var fs = node.fluidInputs.get(i);
            final float total = ops * fs.left().amount * fs.rightFloat() * throughput;
            return formatRate(total / sec) + "/s "
                + fs.left()
                    .getLocalizedName();
        }, PlannhColors.ACCENT_BLUE3.getColor(), false);

        drawIOList(x, y, node.fluidOutputs, i -> {
            final var fs = node.fluidOutputs.get(i);
            final float total = ops * fs.left().amount * fs.rightFloat() * throughput;
            return formatRate(total / sec) + "/s "
                + fs.left()
                    .getLocalizedName();
        }, PlannhColors.ACCENT_CYAN.getColor(), true);
    }

    private int drawIOList(final int x, int y, final List<?> items,
        final java.util.function.IntFunction<String> labelFn, final int color, final boolean indented) {
        for (int i = 0; i < items.size(); i++) {
            final String label = labelFn.apply(i);
            if (label == null) continue;
            GuiDraw.drawText(label, x + (indented ? LIST_INDENT : 0), y, 1.0f, color, false);
            y += LINE_H;
        }
        return y;
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
            if (GuiHelper.isInsideCloseButton(mx, my, canvas.getZoom(), getArea().width, CLOSE_W, CLOSE_MARGIN)) {
                canvas.removeNode(node.id);
                return Result.SUCCESS;
            }

            final float z = canvas.getZoom();
            final int ux = Math.round(mx / z);
            final int uy = Math.round(my / z);

            if (neiWidget != null) {
                final int cw = neiWidget.w + NEI_PAD_W;
                if (ux >= cw - GEAR_HIT_LEFT_OFF && ux <= cw - GEAR_HIT_RIGHT_OFF
                    && uy >= GEAR_HIT_TOP
                    && uy <= GEAR_HIT_BOTTOM) {
                    configOpen = !configOpen;
                    resizeForZoom(canvas.getZoom());
                    return Result.SUCCESS;
                }
                if (configOpen) {
                    for (final ClickZone zone : configZones) {
                        if (zone.contains(ux, uy)) {
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
            final float z = canvas.getZoom();
            node.x = nodeStartX + Math.round(dx / z);
            node.y = nodeStartY + Math.round(dy / z);
            canvas.clampNodeToGroup(node);
            syncTransform(z, canvas.getPanX(), canvas.getPanY());
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

        final int x = LEFT_CONTENT_X;
        final int y0 = CONTENT_TOP + neiWidget.h + THROUGHPUT_GAP + calcInfoHeight();
        final MachineProfile profile = node.machineConfig.getProfile();
        final int panelH = profile.settings()
            .size() * LINE_H + 4;
        GuiDraw.drawRect(
            x - CONFIG_PANEL_INSET,
            y0 - CONFIG_PANEL_INSET,
            CONFIG_PANEL_W,
            panelH,
            PlannhColors.SETTINGS_PANEL_BG.getColor());

        final MachineConfig c = node.machineConfig;
        int y = y0;

        for (final SettingDef<?> def : profile.settings()) {
            y = drawSetting(x, y, def, c);
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
        return profile.settings()
            .size() * LINE_H + 8;
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
        resizeForZoom(canvas.getZoom());
    }

    private void openNeiRecipe() {
        if (!node.outputs.isEmpty()) {
            final ItemStack stack = node.outputs.getFirst()
                .left();
            GuiCraftingRecipe.openRecipeGui("item", stack);
        }
    }
}
