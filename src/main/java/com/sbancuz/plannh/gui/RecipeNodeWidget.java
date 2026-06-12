package com.sbancuz.plannh.gui;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

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

    private static final DrawableResource BG_TEXTURE = new DrawableBuilder(
        "nei:textures/gui/recipebg.png",
        0,
        0,
        184,
        174).build();

    @Getter
    private final Node node;
    private final CanvasWidget canvas;

    private boolean dragging = false;
    private int dragStartMouseX, dragStartMouseY;
    private int nodeStartX, nodeStartY;

    private boolean doubleClickPending = false;
    private final GuiHelper.DoubleClickDetector doubleClick = new GuiHelper.DoubleClickDetector();

    private RecipeHandlerRef handlerRef;
    private NEIRecipeWidget neiWidget;
    private String recipeName = "";
    private boolean handlerInitFailed = false;
    private long lastHandlerUpdate = 0;
    private boolean configOpen = false;
    private final List<ClickZone> configZones = new ArrayList<>();

    private record ClickZone(int ux1, int uy1, int ux2, int uy2, Runnable action) {

        boolean contains(int ux, int uy) {
            return ux >= ux1 && ux < ux2 && uy >= uy1 && uy < uy2;
        }
    }

    public RecipeNodeWidget(Node node, CanvasWidget canvas) {
        this.node = node;
        this.canvas = canvas;
        float z = canvas.getZoom();
        size(Math.round(BASE_W * z), Math.round(BASE_H * z));
    }

    public void syncTransform(float zoom, float panX, float panY) {
        int sx = Math.round(node.x * zoom + panX);
        int sy = Math.round(node.y * zoom + panY);
        pos(sx, sy);
        resizeForZoom(zoom);
    }

    private int zq(float v) {
        return GuiHelper.zq(v, canvas.getZoom());
    }

    private int groupColor(Group g) {
        return g.colorOverride != 0 ? g.colorOverride : PlannhColors.titleColor(g.title);
    }

    private NodeBalance getNodeBalance() {
        BalanceResult br = canvas.getGraph()
            .balance();
        return br.nodeBalances()
            .get(node.id);
    }

    private int calcInfoHeight() {
        int lines = node.inputs.size() + node.outputs.size() + node.fluidInputs.size() + node.fluidOutputs.size();
        return lines * LINE_H + 6;
    }

    private void ensureRecipeHandler() {
        if (handlerRef != null || handlerInitFailed) return;

        RecipeHandlerRef ref = RecipeHandlerRef.of(node.recipeId);
        if (ref == null) {
            handlerInitFailed = true;
            return;
        }

        this.handlerRef = ref;
        this.neiWidget = new NEIRecipeWidget(ref);
        this.neiWidget.showAsWidget(true);
        this.neiWidget.x = 5;
        this.neiWidget.y = 17;

        this.recipeName = ref.handler.getRecipeName()
            .trim();
        resizeForZoom(canvas.getZoom());
    }

    private void setAreaSize(int pw, int ph) {
        getArea().width = pw;
        getArea().height = ph;
        size(pw, ph);
    }

    private void resizeForZoom(float z) {
        if (handlerRef != null) {
            int cw = neiWidget.w + 10;
            int ch = neiWidget.h + 22 + calcInfoHeight() + computeConfigPanelHeight();
            setAreaSize(Math.round((cw + 16) * z), Math.round((ch + 16) * z));
        } else {
            setAreaSize(Math.round(BASE_W * z), Math.round(BASE_H * z));
        }
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        ensureRecipeHandler();

        float z = canvas.getZoom();
        if (neiWidget != null) {
            long now = Minecraft.getSystemTime();
            if (now - lastHandlerUpdate > 50) {
                lastHandlerUpdate = now;
                handlerRef.handler.onUpdate();
            }

            glPushAttrib(GL_ENABLE_BIT | GL_LIGHTING_BIT | GL_COLOR_BUFFER_BIT);
            glTranslatef(0, 0, 400);
            GuiContainerManager.enable2DRender();
            glColor4f(1, 1, 1, 1);

            glPushMatrix();
            glScalef(z, z, 1);

            int cw = neiWidget.w + 10;
            int ch = neiWidget.h + 22;

            BG_TEXTURE.draw(-4, -4, cw + 8, ch + 8, 9, 9, 9, 9);

            glEnable(GL_TEXTURE_2D);
            int titleCol = PlannhColors.titleColor(recipeName);
            GuiDraw.drawRect(5, 5, cw - 10, 12, titleCol);
            GuiDraw.drawRect(5, 17, cw - 10, 1, PlannhColors.NODE_TITLE_LINE.getColor());
            int titleW = Minecraft.getMinecraft().fontRenderer.getStringWidth(recipeName);
            GuiDraw.drawText(
                recipeName,
                (float) neiWidget.w / 2 - (float) titleW / 2,
                7,
                1.0f,
                PlannhColors.TEXT_WHITE.getColor(),
                false);

            if (node.machineConfig.hasAnyBoost()) {
                GuiDraw.drawText(buildConfigBadge(), 8, 7, 1.0f, PlannhColors.TEXT_BADGE.getColor(), false);
            }

            Group grp = canvas.getGroupForNode(node.id);
            if (grp != null) {
                int gc = groupColor(grp);
                String gl = "\u229f " + grp.title;
                int glW = Minecraft.getMinecraft().fontRenderer.getStringWidth(gl);
                GuiDraw.drawText(gl, cw - glW - 16, 7, 1.0f, gc, false);
            }

            GuiDraw.drawText(
                "\u2699",
                cw - 14,
                6,
                1.0f,
                configOpen ? PlannhColors.ACCENT_GREEN.getColor() : PlannhColors.TEXT_DIM.getColor(),
                false);

            neiWidget.draw(5, 17);
            drawThroughputInfo();
            drawConfigContent();

            glPopMatrix();
            drawCloseButtonPixel(getArea().width, getArea().height);
            drawPorts();
            drawGroupMembershipBar();
            glPopAttrib();
            glTranslatef(0, 0, -400);
        } else {
            int w = getArea().width;
            int h = getArea().height;

            GuiDraw.drawRect(0, 0, w, h, PlannhColors.NODE_BG.getColor());
            GuiHelper.drawRectBorder(0, 0, w, h, zq(1), PlannhColors.NODE_BORDER.getColor());

            GuiDraw.drawText(
                node.machineName.isEmpty() ? "?" : node.machineName,
                zq(4),
                zq(3),
                z,
                PlannhColors.TEXT_LIGHT.getColor(),
                false);

            GuiDraw.drawText(
                node.durationTicks + "t ("
                    + String.format("%.1f", (float) node.durationTicks / GuiHelper.TICKS_PER_SECOND)
                    + "s)",
                zq(4),
                h - zq(12),
                z,
                PlannhColors.TEXT_DIM.getColor(),
                false);

            if (!node.outputs.isEmpty()) {
                ItemStack primary = node.outputs.getFirst()
                    .left();
                if (primary != null) {
                    int is = zq(16);
                    GuiDraw.drawItem(primary, w - is - zq(4), zq(14), is, is, context.getCurrentDrawingZ());
                }
            }

            Group grp2 = canvas.getGroupForNode(node.id);
            if (grp2 != null) {
                GuiDraw.drawText("\u229f " + grp2.title, zq(4), zq(16), z, groupColor(grp2), false);
            }

            drawCloseButtonPixel(w, h);
            drawPorts();
            drawGroupMembershipBar();
        }
    }

    private void drawCloseButtonPixel(int w, int h) {
        GuiHelper.drawCloseButton(
            canvas.getZoom(),
            w,
            CLOSE_W,
            CLOSE_MARGIN,
            PlannhColors.BTN_DELETE_BG.getColor(),
            PlannhColors.ACCENT_RED_X.getColor());
    }

    public int getOutputPortAt(int mx, int my) {
        int half = zq(PORT_HALF);
        int px = getArea().width - zq(PORT_SIZE);
        for (int i = 0; i < node.outputs.size(); i++) {
            int py = portTopY(i, 0) - half;
            if (mx >= px && mx < px + zq(PORT_SIZE) && my >= py && my < py + zq(PORT_SIZE)) return i;
        }
        for (int i = 0; i < node.fluidOutputs.size(); i++) {
            int py = portTopY(i, node.outputs.size()) - half;
            if (mx >= px && mx < px + zq(PORT_SIZE) && my >= py && my < py + zq(PORT_SIZE)) return i;
        }
        return -1;
    }

    public int getInputPortAt(int mx, int my) {
        int half = zq(PORT_HALF);
        for (int i = 0; i < node.inputs.size(); i++) {
            int py = portTopY(i, 0) - half;
            if (mx >= 0 && mx < zq(PORT_SIZE) && my >= py && my < py + zq(PORT_SIZE)) return i;
        }
        for (int i = 0; i < node.fluidInputs.size(); i++) {
            int py = portTopY(i, node.inputs.size()) - half;
            if (mx >= 0 && mx < zq(PORT_SIZE) && my >= py && my < py + zq(PORT_SIZE)) return i;
        }
        return -1;
    }

    private void drawGroupMembershipBar() {
        Group group = canvas.getGroupForNode(node.id);
        if (group == null) return;
        int gc = groupColor(group);
        GuiDraw.drawRect(
            0,
            0,
            zq(4),
            getArea().height,
            Color.argb(Color.getRed(gc), Color.getGreen(gc), Color.getBlue(gc), ALPHA_BAR));
    }

    private int portTopY(int i, int totalBefore) {
        float z = canvas.getZoom();
        return Math.round(((totalBefore + i + 1) * PORT_SPACING + PORT_ORIGIN) * z);
    }

    private void drawPorts() {
        int ps = zq(PORT_SIZE);
        int half = zq(PORT_HALF);

        for (int i = 0; i < node.outputs.size(); i++) {
            int py = portTopY(i, 0) - half;
            GuiDraw
                .drawRect(getArea().width - ps - 1, py - 1, ps + 2, ps + 2, PlannhColors.PIN_OUTPUT_HOVER.getColor());
            GuiDraw.drawRect(getArea().width - ps, py, ps, ps, PlannhColors.PIN_OUTPUT.getColor());
        }
        for (int i = 0; i < node.inputs.size(); i++) {
            int py = portTopY(i, 0) - half;
            GuiDraw.drawRect(-1, py - 1, ps + 2, ps + 2, PlannhColors.PIN_INPUT_HOVER.getColor());
            GuiDraw.drawRect(0, py, ps, ps, PlannhColors.PIN_INPUT.getColor());
        }
        for (int i = 0; i < node.fluidOutputs.size(); i++) {
            int py = portTopY(i, node.outputs.size()) - half;
            GuiDraw.drawRect(getArea().width - ps - 1, py - 1, ps + 2, ps + 2, PlannhColors.PIN_FLUID_OUT_H.getColor());
            GuiDraw.drawRect(getArea().width - ps, py, ps, ps, PlannhColors.PIN_FLUID_OUT.getColor());
        }
        for (int i = 0; i < node.fluidInputs.size(); i++) {
            int py = portTopY(i, node.inputs.size()) - half;
            GuiDraw.drawRect(-1, py - 1, ps + 2, ps + 2, PlannhColors.PIN_FLUID_IN_H.getColor());
            GuiDraw.drawRect(0, py, ps, ps, PlannhColors.PIN_FLUID_IN.getColor());
        }
    }

    private void drawThroughputInfo() {
        int x = 8;
        int y = 17 + neiWidget.h + 4;

        NodeBalance nb = getNodeBalance();
        float sec = nb != null && nb.totalDurationTicks > 0 ? (float) nb.totalDurationTicks / GuiHelper.TICKS_PER_SECOND
            : node.durationTicks > 0 ? (float) node.durationTicks / GuiHelper.TICKS_PER_SECOND : 1f;
        int ops = nb != null ? nb.operations : 1;
        int throughput = nb != null ? node.machineConfig.computeEffect(node.properties.asMap(), node.durationTicks)
            .throughputFactor() : 1;

        y = drawIOList(x, y, node.inputs, i -> {
            var pair = node.inputs.get(i);
            if (pair.left() == null) return null;
            float total = nb != null && nb.effectiveInputs.containsKey(i) ? nb.effectiveInputs.get(i)
                : pair.left().stackSize;
            return formatRate(total / sec) + "/s "
                + pair.left()
                    .getDisplayName();
        }, PlannhColors.TEXT_MUTED.getColor(), false);

        y = drawIOList(x, y, node.outputs, i -> {
            var pair = node.outputs.get(i);
            if (pair.left() == null) return null;
            float total = nb != null && nb.effectiveOutputs.containsKey(i) ? nb.effectiveOutputs.get(i)
                : pair.left().stackSize;
            String label = formatRate(total / sec) + "/s "
                + pair.left()
                    .getDisplayName();
            float chance = pair.rightFloat();
            if (chance < 0.999f) label += " (" + Math.round(chance * 100) + "%)";
            return label;
        }, PlannhColors.ACCENT_YELLOW.getColor(), true);

        y = drawIOList(x, y, node.fluidInputs, i -> {
            var fs = node.fluidInputs.get(i);
            float total = ops * fs.left().amount * fs.rightFloat() * throughput;
            return formatRate(total / sec) + "/s "
                + fs.left()
                    .getLocalizedName();
        }, PlannhColors.ACCENT_BLUE3.getColor(), false);

        drawIOList(x, y, node.fluidOutputs, i -> {
            var fs = node.fluidOutputs.get(i);
            float total = ops * fs.left().amount * fs.rightFloat() * throughput;
            return formatRate(total / sec) + "/s "
                + fs.left()
                    .getLocalizedName();
        }, PlannhColors.ACCENT_CYAN.getColor(), true);
    }

    private int drawIOList(int x, int y, List<?> items, java.util.function.IntFunction<String> labelFn, int color,
        boolean indented) {
        for (int i = 0; i < items.size(); i++) {
            String label = labelFn.apply(i);
            if (label == null) continue;
            GuiDraw.drawText(label, x + (indented ? 4 : 0), y, 1.0f, color, false);
            y += LINE_H;
        }
        return y;
    }

    private static String formatRate(float rate) {
        if (rate >= 1000000) return String.format("%.1fM", rate / 1000000);
        if (rate >= 1000) return String.format("%.0f", rate);
        if (rate >= 1) return String.format("%.2f", rate);
        return String.format("%.3f", rate);
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (mouseButton == 0) {
            int mx = getContext().getMouseX();
            int my = getContext().getMouseY();
            if (GuiHelper.isInsideCloseButton(mx, my, canvas.getZoom(), getArea().width, CLOSE_W, CLOSE_MARGIN)) {
                canvas.removeNode(node.id);
                return Result.SUCCESS;
            }

            float z = canvas.getZoom();
            int ux = Math.round(mx / z);
            int uy = Math.round(my / z);

            if (neiWidget != null) {
                int cw = neiWidget.w + 10;
                if (ux >= cw - 18 && ux <= cw - 6 && uy >= 4 && uy <= 16) {
                    configOpen = !configOpen;
                    resizeForZoom(canvas.getZoom());
                    return Result.SUCCESS;
                }
                if (configOpen) {
                    for (ClickZone zone : configZones) {
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
    public boolean onMouseRelease(int mouseButton) {
        if (mouseButton == 0) {
            if (doubleClickPending) {
                doubleClickPending = false;
                openNeiRecipe();
                return true;
            }
            dragging = false;
            canvas.onNodeDragFinished();
            return true;
        }
        return false;
    }

    @Override
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        if (dragging && mouseButton == 0) {
            int dx = getContext().getAbsMouseX() - dragStartMouseX;
            int dy = getContext().getAbsMouseY() - dragStartMouseY;
            float z = canvas.getZoom();
            node.x = nodeStartX + Math.round(dx / z);
            node.y = nodeStartY + Math.round(dy / z);
            canvas.clampNodeToGroup(node);
            syncTransform(z, canvas.getPanX(), canvas.getPanY());
        }
    }

    private String buildConfigBadge() {
        MachineConfig c = node.machineConfig;
        MachineProfile profile = c.getProfile();
        StringBuilder sb = new StringBuilder();

        for (SettingDef<?> def : profile.settings()) {
            Object val = c.settings.get(def.key);
            if (val == null) continue;
            if (val.equals(def.defaultValue)) continue;
            String badge = def.badge(val, c);
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

        int x = 8;
        int y0 = 17 + neiWidget.h + 4 + calcInfoHeight();
        MachineProfile profile = node.machineConfig.getProfile();
        int panelH = profile.settings()
            .size() * LINE_H + 4;
        GuiDraw.drawRect(x - 2, y0 - 2, 170, panelH, PlannhColors.SETTINGS_PANEL_BG.getColor());

        MachineConfig c = node.machineConfig;
        int y = y0;

        for (SettingDef<?> def : profile.settings()) {
            y = drawSetting(x, y, def, c);
        }
    }

    private int drawSetting(int x, int y, SettingDef<?> def, MachineConfig c) {
        if (def.type == Integer.class) {
            return drawConfigIntField(x, y, def.label, c.getInt(def.key), def.minInt, def.maxInt, v -> {
                c.setInt(def.key, v);
                onConfigChanged();
            });
        } else if (def.type == Boolean.class) {
            boolean val = c.getBoolean(def.key);
            String label = (val ? "[\u2713] " : "[  ] ") + def.label;
            GuiDraw.drawText(
                label,
                x,
                y,
                1.0f,
                val ? PlannhColors.SETTING_ON.getColor() : PlannhColors.SETTING_OFF.getColor(),
                false);
            configZones.add(new ClickZone(x, y, x + 120, y + 10, () -> {
                c.setBoolean(def.key, !val);
                onConfigChanged();
            }));
            return y + LINE_H;
        } else if (def.type == String.class && def.hasOptions()) {
            String val = c.getString(def.key);
            int optIdx = def.options.indexOf(val);
            if (optIdx < 0) optIdx = 0;
            GuiDraw.drawText(def.label + " " + val, x, y, 1.0f, PlannhColors.SETTING_ON.getColor(), false);

            int decX = x + 80;
            int incX = decX + 22;
            GuiDraw.drawText("[-]", decX, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);
            GuiDraw.drawText("[+]", incX, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);

            configZones.add(new ClickZone(decX, y, incX, y + 10, () -> {
                int cur = def.options.indexOf(c.getString(def.key));
                c.setString(def.key, def.options.get(Math.max(0, (Math.max(cur, 0)) - 1)));
                onConfigChanged();
            }));
            configZones.add(new ClickZone(incX, y, incX + 22, y + 10, () -> {
                int cur = def.options.indexOf(c.getString(def.key));
                c.setString(def.key, def.options.get(Math.min(def.options.size() - 1, (Math.max(cur, 0)) + 1)));
                onConfigChanged();
            }));
            return y + LINE_H;
        }
        return y + LINE_H;
    }

    private int computeConfigPanelHeight() {
        if (!configOpen) return 0;
        MachineProfile profile = node.machineConfig.getProfile();
        return profile.settings()
            .size() * LINE_H + 8;
    }

    private int drawConfigIntField(int x, int y, String label, int value, int min, int max,
        java.util.function.IntConsumer setter) {
        GuiDraw.drawText(label + " " + value, x, y, 1.0f, PlannhColors.TEXT_LIGHT.getColor(), false);
        GuiDraw.drawText("[-]", x + 80, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);
        GuiDraw.drawText("[+]", x + 102, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);

        configZones.add(
            new ClickZone(x + 80, y, x + 102, y + 10, () -> { if (value > min) setter.accept(value - 1); }));
        configZones.add(
            new ClickZone(x + 102, y, x + 124, y + 10, () -> { if (value < max) setter.accept(value + 1); }));
        return y + LINE_H;
    }

    private void onConfigChanged() {
        resizeForZoom(canvas.getZoom());
    }

    private void openNeiRecipe() {
        if (!node.outputs.isEmpty()) {
            ItemStack stack = node.outputs.getFirst()
                .left();
            GuiCraftingRecipe.openRecipeGui("item", stack);
        }
    }
}
