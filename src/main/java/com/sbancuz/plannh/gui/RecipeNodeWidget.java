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
    private long lastClickTime = 0;

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
        getArea().width = Math.round(BASE_W * z);
        getArea().height = Math.round(BASE_H * z);
        size(Math.round(BASE_W * z), Math.round(BASE_H * z));
    }

    public void syncTransform(float zoom, float panX, float panY) {
        int sx = Math.round(node.x * zoom + panX);
        int sy = Math.round(node.y * zoom + panY);
        pos(sx, sy);
        resizeForZoom(zoom);
    }

    private NodeBalance getNodeBalance() {
        BalanceResult br = canvas.getGraph()
            .balance();
        return br.nodeBalances()
            .get(node.id);
    }

    private int calcInfoHeight() {
        int lines = node.inputs.size() + node.outputs.size() + node.fluidInputs.size() + node.fluidOutputs.size();
        return lines * 11 + 6;
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
            int configH = computeConfigPanelHeight();
            int ch = neiWidget.h + 22 + calcInfoHeight() + configH;
            setAreaSize(Math.round((cw + 16) * z), Math.round((ch + 16) * z));
        } else {
            setAreaSize(Math.round(BASE_W * z), Math.round(BASE_H * z));
        }
    }

    private int zq(float v) {
        return Math.round(v * canvas.getZoom());
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
            GuiDraw
                .drawText(recipeName, neiWidget.w / 2 - titleW / 2, 7, 1.0f, PlannhColors.TEXT_WHITE.getColor(), false);

            if (node.machineConfig.hasAnyBoost()) {
                String badge = buildConfigBadge();
                GuiDraw.drawText(badge, 8, 7, 1.0f, PlannhColors.TEXT_BADGE.getColor(), false);
            }

            Group grp = canvas.getGroupForNode(node.id);
            if (grp != null) {
                int gc = grp.colorOverride != 0 ? grp.colorOverride : PlannhColors.titleColor(grp.title);
                String gl = "\u229f " + grp.title;
                int glW = Minecraft.getMinecraft().fontRenderer.getStringWidth(gl);
                GuiDraw.drawText(gl, cw - glW - 16, 7, 1.0f, gc, false);
            }

            GuiDraw.drawText(
                "⚙",
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
            GuiDraw.drawRect(0, 0, w, zq(1), PlannhColors.NODE_BORDER.getColor());
            GuiDraw.drawRect(0, h - zq(1), w, zq(1), PlannhColors.NODE_BORDER.getColor());
            GuiDraw.drawRect(0, 0, zq(1), h, PlannhColors.NODE_BORDER.getColor());
            GuiDraw.drawRect(w - zq(1), 0, zq(1), h, PlannhColors.NODE_BORDER.getColor());

            String machineLabel = node.machineName.isEmpty() ? "?" : node.machineName;
            GuiDraw.drawText(machineLabel, zq(4), zq(3), z, PlannhColors.TEXT_LIGHT.getColor(), false);

            String timeLabel = node.durationTicks + "t (" + String.format("%.1f", node.durationTicks / 20f) + "s)";
            GuiDraw.drawText(timeLabel, zq(4), h - zq(12), z, PlannhColors.TEXT_DIM.getColor(), false);

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
                int gc2 = grp2.colorOverride != 0 ? grp2.colorOverride : PlannhColors.titleColor(grp2.title);
                GuiDraw.drawText("\u229f " + grp2.title, zq(4), zq(16), z, gc2, false);
            }

            drawCloseButtonPixel(w, h);
            drawPorts();
            drawGroupMembershipBar();
        }
    }

    private void drawCloseButtonPixel(int w, int h) {
        int bs = zq(CLOSE_W);
        int bx = w - bs - zq(CLOSE_MARGIN);
        int by = zq(CLOSE_MARGIN);
        GuiDraw.drawRect(bx - 1, by - 1, bs + 2, bs + 2, PlannhColors.BTN_DELETE_SHADOW.getColor());
        GuiDraw.drawRect(bx, by, bs, bs, PlannhColors.BTN_DELETE_BG.getColor());
        int inset = zq(2);
        GuiDraw.drawRect(bx + inset, by + inset, bs - zq(4), bs - zq(4), PlannhColors.BTN_DELETE_INNER.getColor());
        int xw = Minecraft.getMinecraft().fontRenderer.getStringWidth("x");
        GuiDraw.drawText("x", bx + bs / 2 - xw / 2, by + zq(1), 1.0f, PlannhColors.ACCENT_RED_X.getColor(), false);
    }

    private boolean isInsideCloseButton(int mx, int my) {
        int bs = zq(CLOSE_W);
        int bx = getArea().width - bs - zq(CLOSE_MARGIN);
        int by = zq(CLOSE_MARGIN);
        return mx >= bx && mx < bx + bs && my >= by && my < by + bs;
    }

    public int getOutputPortAt(int mx, int my) {
        int half = zq(PORT_HALF);
        int px = getArea().width - zq(PORT_SIZE);
        for (int i = 0; i < node.outputs.size(); i++) {
            int py = zq((i + 1) * 18 + 10) - half;
            if (mx >= px && mx < px + zq(PORT_SIZE) && my >= py && my < py + zq(PORT_SIZE)) {
                return i;
            }
        }
        for (int i = 0; i < node.fluidOutputs.size(); i++) {
            int py = zq((node.outputs.size() + i + 1) * 18 + 10) - half;
            if (mx >= px && mx < px + zq(PORT_SIZE) && my >= py && my < py + zq(PORT_SIZE)) {
                return i;
            }
        }
        return -1;
    }

    public int getInputPortAt(int mx, int my) {
        int half = zq(PORT_HALF);
        for (int i = 0; i < node.inputs.size(); i++) {
            int py = zq((i + 1) * 18 + 10) - half;
            if (mx >= 0 && mx < zq(PORT_SIZE) && my >= py && my < py + zq(PORT_SIZE)) {
                return i;
            }
        }
        for (int i = 0; i < node.fluidInputs.size(); i++) {
            int py = zq((node.inputs.size() + i + 1) * 18 + 10) - half;
            if (mx >= 0 && mx < zq(PORT_SIZE) && my >= py && my < py + zq(PORT_SIZE)) {
                return i;
            }
        }
        return -1;
    }

    private void drawGroupMembershipBar() {
        Group group = canvas.getGroupForNode(node.id);
        if (group == null) return;
        int groupCol = group.colorOverride != 0 ? group.colorOverride : PlannhColors.titleColor(group.title);
        int barW = zq(4);
        GuiDraw.drawRect(
            0,
            0,
            barW,
            getArea().height,
            Color.argb(Color.getRed(groupCol), Color.getGreen(groupCol), Color.getBlue(groupCol), 180));
    }

    private void drawPorts() {
        int ps = zq(PORT_SIZE);
        int half = zq(PORT_HALF);
        float z = canvas.getZoom();

        for (int i = 0; i < node.outputs.size(); i++) {
            int px = getArea().width - ps;
            int py = portTopY(i, 0, z);
            GuiDraw.drawRect(px - 1, py - 1, ps + 2, ps + 2, PlannhColors.PIN_OUTPUT_HOVER.getColor());
            GuiDraw.drawRect(px, py, ps, ps, PlannhColors.PIN_OUTPUT.getColor());
        }

        for (int i = 0; i < node.inputs.size(); i++) {
            int py = portTopY(i, 0, z);
            GuiDraw.drawRect(-1, py - 1, ps + 2, ps + 2, PlannhColors.PIN_INPUT_HOVER.getColor());
            GuiDraw.drawRect(0, py, ps, ps, PlannhColors.PIN_INPUT.getColor());
        }

        for (int i = 0; i < node.fluidOutputs.size(); i++) {
            int px = getArea().width - ps;
            int py = portTopY(i, node.outputs.size(), z);
            GuiDraw.drawRect(px - 1, py - 1, ps + 2, ps + 2, PlannhColors.PIN_FLUID_OUT_H.getColor());
            GuiDraw.drawRect(px, py, ps, ps, PlannhColors.PIN_FLUID_OUT.getColor());
        }

        for (int i = 0; i < node.fluidInputs.size(); i++) {
            int py = portTopY(i, node.inputs.size(), z);
            GuiDraw.drawRect(-1, py - 1, ps + 2, ps + 2, PlannhColors.PIN_FLUID_IN_H.getColor());
            GuiDraw.drawRect(0, py, ps, ps, PlannhColors.PIN_FLUID_IN.getColor());
        }
    }

    /** Port top Y relative to widget, for the i-th port after {@code totalBefore} ports. */
    private int portTopY(int i, int totalBefore, float z) {
        return Math.round(((totalBefore + i + 1) * PORT_SPACING + PORT_ORIGIN) * z) - zq(PORT_HALF);
    }

    private void drawThroughputInfo() {
        int x = 8;
        int y = 17 + neiWidget.h + 4;

        NodeBalance nb = getNodeBalance();
        float totalSec = 1f;
        if (nb != null) {
            totalSec = nb.totalDurationTicks / 20f;
        }
        if (totalSec <= 0f) {
            totalSec = node.durationTicks / 20f;
        }
        if (totalSec <= 0f) totalSec = 1f;

        int ops = nb != null ? nb.operations : 1;
        int tf = 1;
        if (nb != null) {
            var eff = node.machineConfig.computeEffect(node.properties.asMap(), node.durationTicks);
            tf = eff.throughputFactor();
        }

        for (int i = 0; i < node.inputs.size(); i++) {
            var pair = node.inputs.get(i);
            if (pair.left() == null) continue;
            float total = nb != null && nb.effectiveInputs.containsKey(i) ? nb.effectiveInputs.get(i)
                : pair.left().stackSize;
            float rate = total / totalSec;
            GuiDraw.drawText(
                formatRate(rate) + "/s "
                    + pair.left()
                        .getDisplayName(),
                x,
                y,
                1.0f,
                PlannhColors.TEXT_MUTED.getColor(),
                false);
            y += 11;
        }

        for (int i = 0; i < node.outputs.size(); i++) {
            var pair = node.outputs.get(i);
            if (pair.left() == null) continue;
            float total = nb != null && nb.effectiveOutputs.containsKey(i) ? nb.effectiveOutputs.get(i)
                : pair.left().stackSize;
            float rate = total / totalSec;
            String label = formatRate(rate) + "/s "
                + pair.left()
                    .getDisplayName();
            float chance = pair.rightFloat();
            if (chance < 0.999f) {
                label += " (" + Math.round(chance * 100) + "%)";
            }
            GuiDraw.drawText(label, x + 4, y, 1.0f, PlannhColors.ACCENT_YELLOW.getColor(), false);
            y += 11;
        }

        for (var fs : node.fluidInputs) {
            float total = ops * fs.left().amount * fs.rightFloat() * tf;
            float rate = total / totalSec;
            GuiDraw.drawText(
                formatRate(rate) + "/s "
                    + fs.left()
                        .getLocalizedName(),
                x,
                y,
                1.0f,
                PlannhColors.ACCENT_BLUE3.getColor(),
                false);
            y += 11;
        }
        for (var fs : node.fluidOutputs) {
            float total = ops * fs.left().amount * fs.rightFloat() * tf;
            float rate = total / totalSec;
            GuiDraw.drawText(
                formatRate(rate) + "/s "
                    + fs.left()
                        .getLocalizedName(),
                x + 4,
                y,
                1.0f,
                PlannhColors.ACCENT_CYAN.getColor(),
                false);
            y += 11;
        }
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
            if (isInsideCloseButton(mx, my)) {
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

            if (getOutputPortAt(mx, my) >= 0) {
                return Result.IGNORE;
            }
            long now = Minecraft.getSystemTime();
            if (now - lastClickTime < 300) {
                doubleClickPending = true;
                lastClickTime = 0;
                return Result.SUCCESS;
            }
            lastClickTime = now;
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

        if (!sb.isEmpty()) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private void drawConfigContent() {
        configZones.clear();
        if (!configOpen) return;

        int x = 8;
        int y0 = 17 + neiWidget.h + 4 + calcInfoHeight();
        MachineProfile profile = node.machineConfig.getProfile();
        int settingCount = profile.settings()
            .size();
        int panelH = settingCount * 11 + 4;
        GuiDraw.drawRect(x - 2, y0 - 2, 170, panelH, PlannhColors.SETTINGS_PANEL_BG.getColor());

        MachineConfig c = node.machineConfig;
        int y = y0;

        for (SettingDef<?> def : profile.settings()) {
            y = drawSetting(x, y, def, c);
        }
    }

    private int drawSetting(int x, int y, SettingDef<?> def, MachineConfig c) {
        if (def.type == Integer.class) {
            int val = c.getInt(def.key);
            return drawConfigIntField(x, y, def.label, val, def.minInt, def.maxInt, v -> {
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
            return y + 11;
        } else if (def.type == String.class && def.hasOptions()) {
            String val = c.getString(def.key);
            int optIdx = def.options.indexOf(val);
            if (optIdx < 0) optIdx = 0;
            String display = def.label + " " + val;
            GuiDraw.drawText(display, x, y, 1.0f, PlannhColors.SETTING_ON.getColor(), false);

            String dec = "[-]", inc = "[+]";
            int decX = x + 80;
            int incX = decX + 22;
            GuiDraw.drawText(dec, decX, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);
            GuiDraw.drawText(inc, incX, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);

            configZones.add(new ClickZone(decX, y, incX, y + 10, () -> {
                int cur = def.options.indexOf(c.getString(def.key));
                int next = Math.max(0, (cur < 0 ? 0 : cur) - 1);
                c.setString(def.key, def.options.get(next));
                onConfigChanged();
            }));
            configZones.add(new ClickZone(incX, y, incX + 22, y + 10, () -> {
                int cur = def.options.indexOf(c.getString(def.key));
                int next = Math.min(def.options.size() - 1, (cur < 0 ? 0 : cur) + 1);
                c.setString(def.key, def.options.get(next));
                onConfigChanged();
            }));
            return y + 11;
        }
        return y + 11;
    }

    private int computeConfigPanelHeight() {
        if (!configOpen) return 0;
        MachineProfile profile = node.machineConfig.getProfile();
        return profile.settings()
            .size() * 11 + 8;
    }

    private int drawConfigIntField(int x, int y, String label, int value, int min, int max,
        java.util.function.IntConsumer setter) {
        int labelW = 80;
        String dec = "[-]", inc = "[+]";
        int decX = x + labelW;
        int incX = decX + 22;

        String text = label + " " + value;
        GuiDraw.drawText(text, x, y, 1.0f, PlannhColors.TEXT_LIGHT.getColor(), false);
        GuiDraw.drawText(dec, decX, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);
        GuiDraw.drawText(inc, incX, y, 1.0f, PlannhColors.TEXT_MUTED.getColor(), false);

        int finalValue = value;
        configZones.add(new ClickZone(decX, y, incX, y + 10, () -> {
            if (finalValue > min) {
                setter.accept(finalValue - 1);
            }
        }));
        configZones.add(new ClickZone(incX, y, incX + 22, y + 10, () -> {
            if (finalValue < max) {
                setter.accept(finalValue + 1);
            }
        }));

        return y + 11;
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
