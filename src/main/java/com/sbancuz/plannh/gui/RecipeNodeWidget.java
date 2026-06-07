package com.sbancuz.plannh.gui;

import static org.lwjgl.opengl.GL11.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.data.FlowchartNode;

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
    private List<ThroughputLine> inputLines = new ArrayList<>();
    private List<ThroughputLine> outputLines = new ArrayList<>();
    private int recipeDurationTicks;
    private long recipeTotalEu;
    private long lastHandlerUpdate = 0;

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
        recipeTotalEu = node.totalEu;

        for (ItemStack stack : node.inputs) {
            if (stack != null && stack.stackSize > 0) {
                inputLines.add(new ThroughputLine(stack, stack.stackSize, 0));
            }
        }
        for (ItemStack stack : node.outputs) {
            if (stack != null && stack.stackSize > 0) {
                outputLines.add(new ThroughputLine(stack, stack.stackSize, 0));
            }
        }
    }

    private int calcInfoHeight() {
        int lines = 1 + inputLines.size() + outputLines.size();
        return lines * 11 + 6;
    }

    private int countMatchingOutputs(IRecipeHandler handler, int idx) {
        int matches = 0;
        PositionedStack result = handler.getResultStack(idx);
        if (result != null && result.item != null) {
            for (ItemStack saved : node.outputs) {
                if (saved != null && saved.isItemEqual(result.item)) {
                    matches++;
                    break;
                }
            }
        }
        for (PositionedStack ps : handler.getOtherStacks(idx)) {
            if (ps != null && ps.item != null) {
                for (ItemStack saved : node.outputs) {
                    if (saved != null && saved.isItemEqual(ps.item)) {
                        matches++;
                        break;
                    }
                }
            }
        }
        return matches;
    }

    private void ensureRecipeHandler() {
        if (handlerRef != null || handlerInitFailed) return;
        if (node.outputs.isEmpty()) {
            handlerInitFailed = true;
            return;
        }
        try {
            ItemStack stack = node.outputs.get(0);
            if (stack == null) {
                handlerInitFailed = true;
                return;
            }
            ArrayList<ICraftingHandler> handlers = GuiCraftingRecipe.getCraftingHandlers("item", stack);
            if (!handlers.isEmpty()) {
                ICraftingHandler chosen = null;
                int chosenIdx = 0;
                int bestScore = 0;

                if (!node.recipeOwner.isEmpty()) {
                    for (ICraftingHandler h : handlers) {
                        String ident = h.getOverlayIdentifier();
                        if (ident != null && ident.equals(node.recipeOwner)) {
                            int n = h.numRecipes();
                            for (int idx = 0; idx < n; idx++) {
                                int score = countMatchingOutputs(h, idx);
                                if (score > bestScore) {
                                    bestScore = score;
                                    chosen = h;
                                    chosenIdx = idx;
                                }
                            }
                            break;
                        }
                    }
                }

                if (chosen == null || bestScore == 0) {
                    for (ICraftingHandler h : handlers) {
                        int n = h.numRecipes();
                        for (int idx = 0; idx < n; idx++) {
                            int score = countMatchingOutputs(h, idx);
                            if (score > bestScore) {
                                bestScore = score;
                                chosen = h;
                                chosenIdx = idx;
                            }
                        }
                    }
                }

                if (chosen == null || bestScore == 0) {
                    handlerInitFailed = true;
                    return;
                }

                RecipeHandlerRef ref = RecipeHandlerRef.of(chosen, chosenIdx);
                if (ref != null) {
                    handlerRef = ref;
                    neiWidget = new NEIRecipeWidget(ref);
                    neiWidget.showAsWidget(true);
                    neiWidget.x = 5;
                    neiWidget.y = 17;
                    IRecipeHandler h = ref.handler;
                    recipeName = h.getRecipeName()
                        .trim();
                    extractThroughput();
                    resizeForZoom(canvas.getZoom());
                } else {
                    handlerInitFailed = true;
                }
            } else {
                handlerInitFailed = true;
            }
        } catch (Exception e) {
            handlerInitFailed = true;
        }
    }

    private void setAreaSize(int pw, int ph) {
        getArea().width = pw;
        getArea().height = ph;
        size(pw, ph);
    }

    private void resizeForZoom(float z) {
        if (handlerRef != null) {
            int cw = neiWidget.w + 10;
            int ch = neiWidget.h + 22 + calcInfoHeight();
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

            neiWidget.draw(5, 17);

            drawThroughputInfo();

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
                ItemStack primary = node.outputs.get(0);
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
    }

    private void drawThroughputInfo() {
        int x = 8;
        int y = 17 + neiWidget.h + 4;

        float sec = recipeDurationTicks / 20f;
        String durStr = "Duration: " + recipeDurationTicks + "t";
        if (sec > 0) durStr += " (" + String.format("%.1f", sec) + "s)";
        codechicken.lib.gui.GuiDraw.drawString(durStr, x, y, 0xCCCCCC, false);
        y += 11;

        for (ThroughputLine line : inputLines) {
            String label = line.count + "x " + line.stack.getDisplayName();
            codechicken.lib.gui.GuiDraw.drawString(label, x, y, 0xAAAAAA, false);
            y += 11;
        }
        for (ThroughputLine line : outputLines) {
            String label = line.count + "x " + line.stack.getDisplayName();
            codechicken.lib.gui.GuiDraw.drawString(label, x + 4, y, 0xFFFFAA, false);
            y += 11;
        }
        if (recipeTotalEu > 0) {
            long euPerTick = recipeDurationTicks > 0 ? recipeTotalEu / recipeDurationTicks : 0;
            codechicken.lib.gui.GuiDraw
                .drawString("EU: " + euPerTick + " EU/t (total: " + recipeTotalEu + ")", x, y, 0x88AAFF, false);
        }
    }

    @Override
    public Interactable.Result onMousePressed(int mouseButton) {
        if (mouseButton == 0) {
            int mx = getContext().getMouseX();
            int my = getContext().getMouseY();
            if (isInsideCloseButton(mx, my)) {
                canvas.removeNode(node.id);
                return Result.SUCCESS;
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

    private void openNeiRecipe() {
        if (!node.outputs.isEmpty()) {
            ItemStack stack = node.outputs.get(0);
            GuiCraftingRecipe.openRecipeGui("item", stack);
        }
    }
}
