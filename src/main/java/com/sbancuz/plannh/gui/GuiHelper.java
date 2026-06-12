package com.sbancuz.plannh.gui;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.drawable.GuiDraw;

public final class GuiHelper {

    public static final int TICKS_PER_SECOND = 20;

    public static int zq(float v, float zoom) {
        return Math.round(v * zoom);
    }

    public static void drawRectBorder(int x, int y, int w, int h, int bw, int color) {
        GuiDraw.drawRect(x, y, w, bw, color);
        GuiDraw.drawRect(x, y + h - bw, w, bw, color);
        GuiDraw.drawRect(x, y, bw, h, color);
        GuiDraw.drawRect(x + w - bw, y, bw, h, color);
    }

    public static void drawCloseButton(float zoom, int pw, int closeW, int closeMargin, int color, int textColor) {
        int bs = zq(closeW, zoom);
        int bx = pw - bs - zq(closeMargin, zoom);
        int by = zq(closeMargin, zoom);
        GuiDraw.drawRect(bx - 1, by - 1, bs + 2, bs + 2, PlannhColors.BTN_DELETE_SHADOW.getColor());
        GuiDraw.drawRect(bx, by, bs, bs, color);
        int inset = zq(2, zoom);
        GuiDraw.drawRect(
            bx + inset,
            by + inset,
            bs - zq(4, zoom),
            bs - zq(4, zoom),
            PlannhColors.BTN_DELETE_INNER.getColor());
        int xw = Minecraft.getMinecraft().fontRenderer.getStringWidth("x");
        GuiDraw.drawText("x", bx + bs / 2 - xw / 2, by + zq(1, zoom), 1.0f, textColor, false);
    }

    public static boolean isInsideCloseButton(int mx, int my, float zoom, int pw, int closeW, int closeMargin) {
        int bs = zq(closeW, zoom);
        int bx = pw - bs - zq(closeMargin, zoom);
        int by = zq(closeMargin, zoom);
        return mx >= bx && mx < bx + bs && my >= by && my < by + bs;
    }

    public static class DoubleClickDetector {

        private long lastClickTime = 0;

        public boolean check() {
            long now = Minecraft.getSystemTime();
            if (now - lastClickTime < 300) {
                lastClickTime = 0;
                return true;
            }
            lastClickTime = now;
            return false;
        }
    }
}
