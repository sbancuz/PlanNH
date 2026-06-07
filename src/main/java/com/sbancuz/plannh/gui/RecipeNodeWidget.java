package com.sbancuz.plannh.gui;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.List;

import codechicken.nei.util.FavoriteStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.FlowchartBalancer.BalanceResult;
import com.sbancuz.plannh.data.FlowchartBalancer.NodeBalance;
import com.sbancuz.plannh.data.FlowchartNode;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeProperty;

import codechicken.nei.PositionedStack;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.NEIRecipeWidget;
import codechicken.nei.recipe.RecipeHandlerRef;
import lombok.Getter;

public class RecipeNodeWidget extends Widget<RecipeNodeWidget> implements Interactable {

    private static class ThroughputLine {

        final ItemStack stack;
        final int count;
        final float perSec;

        ThroughputLine(ItemStack stack, int count, float durationSec) {
            this.stack = stack;
            this.count = count;
            this.perSec = durationSec > 0 ? count / durationSec : 0;
        }
    }

    private static final int BASE_W = 120;
    private static final int BASE_H = 80;
    private static final int CLOSE_W = 12;
    private static final int CLOSE_MARGIN = 2;
    private static final int PORT_SIZE = 8;
    private static final int PORT_HALF = PORT_SIZE / 2;

    private static final DrawableResource BG_TEXTURE = new DrawableBuilder(
        "nei:textures/gui/recipebg.png",
        0,
        0,
        184,
        174).build();

    @Getter
    private final FlowchartNode node;
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
    private final List<ThroughputLine> inputLines = new ArrayList<>();
    private final List<ThroughputLine> outputLines = new ArrayList<>();
    private int recipeDurationTicks;
    private long lastHandlerUpdate = 0;
    private boolean configOpen = false;
    private final List<ClickZone> configZones = new java.util.ArrayList<>();

    private record ClickZone(int ux1, int uy1, int ux2, int uy2, Runnable action) {

        boolean contains(int ux, int uy) {
            return ux >= ux1 && ux < ux2 && uy >= uy1 && uy < uy2;
        }
    }

    public RecipeNodeWidget(FlowchartNode node, CanvasWidget canvas) {
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

    private void extractThroughput() {
        recipeDurationTicks = node.durationTicks;

        for (var p : node.inputs) {
            ItemStack stack = p.left();
            if (stack != null && stack.stackSize > 0) {
                inputLines.add(new ThroughputLine(stack, stack.stackSize, 0));
            }
        }
        for (var p : node.outputs) {
            ItemStack stack = p.left();
            if (stack != null && stack.stackSize > 0) {
                outputLines.add(new ThroughputLine(stack, stack.stackSize, 0));
            }
        }
    }

    private NodeBalance getNodeBalance() {
        BalanceResult br = canvas.getGraph()
            .balance();
        return br.nodeBalances()
            .get(node.id);
    }

    private int calcInfoHeight() {
        int lines = 0;
        NodeBalance nb = getNodeBalance();
        int ops = nb != null ? nb.operations : 1;
        boolean hasEnergy = nb != null && nb.totalEnergy > 0;

        if (ops > 1) lines++;
        if (recipeDurationTicks > 0) lines++;
        if (hasEnergy) lines++;
        lines += inputLines.size() + outputLines.size();
        lines += node.fluidInputs.size() + node.fluidOutputs.size();
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

        this.recipeName = ref.handler.getRecipeName().trim();
        extractThroughput();
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
            codechicken.lib.gui.GuiDraw.drawRect(5, 5, cw - 10, 12, 0x30000000);
            codechicken.lib.gui.GuiDraw.drawStringC(recipeName, neiWidget.w / 2, 7, 0xFFFFFF);

            if (node.machineConfig.hasAnyBoost()) {
                String badge = buildConfigBadge();
                codechicken.lib.gui.GuiDraw.drawString(badge, 8, 7, 0x88AAFF, false);
            }

            codechicken.lib.gui.GuiDraw.drawString("⚙", cw - 14, 6, configOpen ? 0x88FF88 : 0x888888, false);

            neiWidget.draw(5, 17);

            drawThroughputInfo();
            drawConfigContent();

            glPopMatrix();
            drawCloseButtonPixel(getArea().width, getArea().height);
            drawPorts();
            glPopAttrib();
            glTranslatef(0, 0, -400);
        } else {
            int w = getArea().width;
            int h = getArea().height;

            GuiDraw.drawRect(0, 0, w, h, Color.argb(50, 50, 50, 230));
            GuiDraw.drawRect(0, 0, w, zq(1), Color.argb(100, 150, 200, 255));
            GuiDraw.drawRect(0, h - zq(1), w, zq(1), Color.argb(100, 150, 200, 255));
            GuiDraw.drawRect(0, 0, zq(1), h, Color.argb(100, 150, 200, 255));
            GuiDraw.drawRect(w - zq(1), 0, zq(1), h, Color.argb(100, 150, 200, 255));

            String machineLabel = node.machineName.isEmpty() ? "?" : node.machineName;
            GuiDraw.drawText(machineLabel, zq(4), zq(3), z, 0xCCCCCC, false);

            String timeLabel = node.durationTicks + "t (" + String.format("%.1f", node.durationTicks / 20f) + "s)";
            GuiDraw.drawText(timeLabel, zq(4), h - zq(12), z, 0x888888, false);

            if (!node.outputs.isEmpty()) {
                ItemStack primary = node.outputs.getFirst()
                    .left();
                if (primary != null) {
                    int is = zq(16);
                    GuiDraw.drawItem(primary, w - is - zq(4), zq(14), is, is, context.getCurrentDrawingZ());
                }
            }

            drawCloseButtonPixel(w, h);
            drawPorts();
        }
    }

    private void drawCloseButtonPixel(int w, int h) {
        int bs = zq(CLOSE_W);
        int bx = w - bs - zq(CLOSE_MARGIN);
        int by = zq(CLOSE_MARGIN);
        int inset = zq(2);
        GuiDraw.drawRect(bx, by, bs, bs, Color.argb(180, 200, 40, 40));
        GuiDraw.drawRect(bx + inset, by + inset, bs - zq(4), bs - zq(4), Color.argb(220, 0, 0, 0));
        codechicken.lib.gui.GuiDraw.drawStringC("x", bx + bs / 2, by + zq(1), 0xFF5555);
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

    private void drawPorts() {
        int ps = zq(PORT_SIZE);
        int half = zq(PORT_HALF);

        for (int i = 0; i < node.outputs.size(); i++) {
            int px = getArea().width - ps;
            int py = zq((i + 1) * 18 + 10) - half;
            GuiDraw.drawRect(px, py, ps, ps, Color.argb(220, 100, 200, 100));
        }

        for (int i = 0; i < node.inputs.size(); i++) {
            int py = zq((i + 1) * 18 + 10) - half;
            GuiDraw.drawRect(0, py, ps, ps, Color.argb(220, 100, 100, 200));
        }

        for (int i = 0; i < node.fluidOutputs.size(); i++) {
            int px = getArea().width - ps;
            int py = zq((node.outputs.size() + i + 1) * 18 + 10) - half;
            GuiDraw.drawRect(px, py, ps, ps, Color.argb(220, 100, 200, 100));
        }

        for (int i = 0; i < node.fluidInputs.size(); i++) {
            int py = zq((node.inputs.size() + i + 1) * 18 + 10) - half;
            GuiDraw.drawRect(0, py, ps, ps, Color.argb(220, 100, 100, 200));
        }
    }

    private void drawThroughputInfo() {
        int x = 8;
        int y = 17 + neiWidget.h + 4;

        NodeBalance nb = getNodeBalance();

        int ops = nb != null ? nb.operations : 1;
        int totalDur = nb != null ? nb.totalDurationTicks : recipeDurationTicks;
        long totalEnergy = nb != null ? nb.totalEnergy : 0;

        if (ops > 1) {
            codechicken.lib.gui.GuiDraw.drawString("Operations: " + ops + "\u00d7", x, y, 0x88AAFF, false);
            y += 11;
        }

        if (recipeDurationTicks > 0) {
            int durPerOp = nb != null ? nb.durationPerOp : recipeDurationTicks;
            float secPerOp = durPerOp / 20f;
            String durStr = durPerOp + "t";
            if (secPerOp > 0) durStr += " (" + String.format("%.1f", secPerOp) + "s)";
            if (ops > 1) {
                durStr += "  \u00d7" + ops + " = " + totalDur + "t";
                float totalSec = totalDur / 20f;
                if (totalSec > 0) durStr += " (" + String.format("%.1f", totalSec) + "s)";
            }
            codechicken.lib.gui.GuiDraw.drawString(durStr, x, y, 0xCCCCCC, false);
            y += 11;
        }

        if (totalEnergy > 0) {
            String energyLabel = "Total Energy: " + formatEnergy(totalEnergy);
            codechicken.lib.gui.GuiDraw.drawString(energyLabel, x, y, 0x88AAFF, false);
            y += 11;
        }

        for (ThroughputLine line : inputLines) {
            String label = line.count + "x " + line.stack.getDisplayName();
            codechicken.lib.gui.GuiDraw.drawString(label, x, y, 0xAAAAAA, false);
            y += 11;
        }
        int outIdx = 0;
        for (ThroughputLine line : outputLines) {
            String label = line.count + "x " + line.stack.getDisplayName();
            float chance = node.outputs.get(outIdx)
                .rightFloat();
            if (chance < 0.999f) {
                label += " (" + Math.round(chance * 100) + "%)";
            }
            codechicken.lib.gui.GuiDraw.drawString(label, x + 4, y, 0xFFFFAA, false);
            y += 11;
            outIdx++;
        }

        if (!node.fluidInputs.isEmpty()) {
            for (var fs : node.fluidInputs) {
                String label = formatFluidAmount(fs.left().amount) + " "
                    + fs.left()
                        .getLocalizedName();
                codechicken.lib.gui.GuiDraw.drawString(label, x, y, 0x77AAFF, false);
                y += 11;
            }
        }
        if (!node.fluidOutputs.isEmpty()) {
            for (var fs : node.fluidOutputs) {
                String label = formatFluidAmount(fs.left().amount) + " "
                    + fs.left()
                        .getLocalizedName();
                codechicken.lib.gui.GuiDraw.drawString(label, x + 4, y, 0x77FFAA, false);
                y += 11;
            }
        }
    }

    private String formatProperty(RecipeProperty<?> prop, Object value) {
        if (prop == RecipePropertyAPI.TOTAL_EU) {
            long eu = (Long) value;
            long euPerTick = recipeDurationTicks > 0 ? eu / recipeDurationTicks : 0;
            return "EU: " + euPerTick + " EU/t (total: " + eu + ")";
        }
        if (prop == RecipePropertyAPI.EU_PER_TICK) {
            return "EU/t: " + value;
        }
        return prop.getDisplayName() + ": " + value;
    }

    private static String formatEnergy(long energy) {
        if (energy >= 1000000) return energy / 1000 + "k";
        if (energy >= 1000) return String.format("%.1fk", energy / 1000.0);
        return String.valueOf(energy);
    }

    private static String formatFluidAmount(int mb) {
        if (mb >= 1000) return (mb / 1000) + "." + ((mb % 1000) / 100) + "B";
        return mb + "mB";
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
            syncTransform(z, canvas.getPanX(), canvas.getPanY());
        }
    }

    private static String voltageTier(long v) {
        if (v <= 0) return "";
        int t = (int) Math.round(Math.log(v / 8.0) / Math.log(4));
        if (t < 0) return "";
        String[] names = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV",
            "MAX" };
        return t < names.length ? names[t] : "T" + t;
    }

    private static long voltageForTier(int idx) {
        return (long) (8 * Math.pow(4, idx));
    }

    private String buildConfigBadge() {
        MachineConfig c = node.machineConfig;
        StringBuilder sb = new StringBuilder();
        if (c.speedBoostPercent != 100) sb.append("⏱")
            .append(c.speedBoostPercent)
            .append("% ");
        if (c.maxParallel > 1) sb.append("∥")
            .append(c.maxParallel)
            .append(" ");
        if (c.machineCount > 1) sb.append("×")
            .append(c.machineCount)
            .append(" ");
        if (c.machineVoltage > 0) {
            sb.append(voltageTier(c.machineVoltage));
            if (c.perfectOC) sb.append("P");
            sb.append(" ");
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private void drawConfigContent() {
        configZones.clear();
        if (!configOpen) return;

        int x = 8;
        int y0 = 17 + neiWidget.h + 4 + calcInfoHeight();

        int panelH = 6 * 11 + 4;
        codechicken.lib.gui.GuiDraw.drawRect(x - 2, y0 - 2, 170, panelH, 0xAA202020);

        MachineConfig c = node.machineConfig;
        int y = y0;

        int tierIdx = c.machineVoltage > 0 ? (int) Math.round(Math.log(c.machineVoltage / 8.0) / Math.log(4)) : -1;
        y = drawConfigTierField(x, y, "Tier", tierIdx, v -> {
            c.machineVoltage = v >= 0 ? voltageForTier(v) : 0;
            onConfigChanged();
        }, y0);

        y = drawConfigIntField(x, y, "Amp", (int) c.machineAmperage, 1, 64, v -> {
            c.machineAmperage = v;
            onConfigChanged();
        }, y0);
        y = drawConfigIntField(x, y, "Speed %", c.speedBoostPercent, 10, 10000, v -> {
            c.speedBoostPercent = v;
            onConfigChanged();
        }, y0);
        y = drawConfigIntField(x, y, "Par  ", c.maxParallel, 1, 4096, v -> {
            c.maxParallel = v;
            onConfigChanged();
        }, y0);
        y = drawConfigIntField(x, y, "Mach ", c.machineCount, 1, 4096, v -> {
            c.machineCount = v;
            onConfigChanged();
        }, y0);

        String pocLabel = c.perfectOC ? "[\u2713] Perfect OC" : "[  ] Perfect OC";
        codechicken.lib.gui.GuiDraw.drawString(pocLabel, x, y, c.perfectOC ? 0x88FF88 : 0x888888, false);
        configZones.add(new ClickZone(x, y, x + 90, y + 10, () -> {
            c.perfectOC = !c.perfectOC;
            onConfigChanged();
        }));
        y += 11;
    }

    private int computeConfigPanelHeight() {
        return configOpen ? 6 * 11 + 8 : 0;
    }

    private int drawConfigTierField(int x, int y, String label, int currentTierIdx,
        java.util.function.IntConsumer setter, int y0) {
        String[] names = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV",
            "MAX" };
        int displayIdx = currentTierIdx >= 0 ? currentTierIdx : 0;
        String tierName = currentTierIdx >= 0 ? names[Math.min(displayIdx, names.length - 1)] : "OFF";
        String text = label + " " + tierName;

        String dec = "[-]", inc = "[+]";
        int decX = x + 60;
        int incX = decX + 22;

        codechicken.lib.gui.GuiDraw.drawString(text, x, y, currentTierIdx >= 0 ? 0x88FF88 : 0x888888, false);
        codechicken.lib.gui.GuiDraw.drawString(dec, decX, y, 0xAAAAAA, false);
        codechicken.lib.gui.GuiDraw.drawString(inc, incX, y, 0xAAAAAA, false);

        configZones.add(new ClickZone(decX, y, incX, y + 10, () -> {
            int cur = node.machineConfig.machineVoltage > 0
                ? (int) Math.round(Math.log(node.machineConfig.machineVoltage / 8.0) / Math.log(4))
                : 15;
            int next = Math.max(-1, cur - 1);
            setter.accept(next);
        }));
        configZones.add(new ClickZone(incX, y, incX + 22, y + 10, () -> {
            int cur = node.machineConfig.machineVoltage > 0
                ? (int) Math.round(Math.log(node.machineConfig.machineVoltage / 8.0) / Math.log(4))
                : -1;
            int next = Math.min(14, cur + 1);
            setter.accept(next);
        }));

        return y + 11;
    }

    private int drawConfigIntField(int x, int y, String label, int value, int min, int max,
        java.util.function.IntConsumer setter, int y0) {
        int labelW = 70;
        String dec = "[-]", inc = "[+]";
        int decX = x + labelW;
        int incX = decX + 22;

        String text = label + " " + value;
        codechicken.lib.gui.GuiDraw.drawString(text, x, y, 0xCCCCCC, false);
        codechicken.lib.gui.GuiDraw.drawString(dec, decX, y, 0xAAAAAA, false);
        codechicken.lib.gui.GuiDraw.drawString(inc, incX, y, 0xAAAAAA, false);

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
