package com.sbancuz.plannh.gui;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.drawable.GuiDraw;

public final class GuiHelper {

    public static final int TICKS_PER_SECOND = 20;

    public static int zq(final float v, final float zoom) {
        return Math.round(v * zoom);
    }

    public static void drawRectBorder(final int x, final int y, final int w, final int h, final int bw,
        final int color) {
        GuiDraw.drawRect(x, y, w, bw, color);
        GuiDraw.drawRect(x, y + h - bw, w, bw, color);
        GuiDraw.drawRect(x, y, bw, h, color);
        GuiDraw.drawRect(x + w - bw, y, bw, h, color);
    }

    public static void drawCloseButton(final float zoom, final int pw, final int closeW, final int closeMargin,
        final int color, final int textColor) {
        final int bs = zq(closeW, zoom);
        final int bx = pw - bs - zq(closeMargin, zoom);
        final int by = zq(closeMargin, zoom);
        GuiDraw.drawRect(bx - 1, by - 1, bs + 2, bs + 2, PlannhColors.BTN_DELETE_SHADOW.getColor());
        GuiDraw.drawRect(bx, by, bs, bs, color);
        final int inset = zq(2, zoom);
        GuiDraw.drawRect(
            bx + inset,
            by + inset,
            bs - zq(4, zoom),
            bs - zq(4, zoom),
            PlannhColors.BTN_DELETE_INNER.getColor());
        final int xw = Minecraft.getMinecraft().fontRenderer.getStringWidth("x");
        GuiDraw.drawText("x", bx + (float) bs / 2 - (float) xw / 2, by + zq(1, zoom), 1.0f, textColor, false);
    }

    public static boolean isInsideCloseButton(final int mx, final int my, final float zoom, final int pw,
        final int closeW, final int closeMargin) {
        final int bs = zq(closeW, zoom);
        final int bx = pw - bs - zq(closeMargin, zoom);
        final int by = zq(closeMargin, zoom);
        return mx >= bx && mx < bx + bs && my >= by && my < by + bs;
    }

    public static class DoubleClickDetector {

        private long lastClickTime = 0;

        public boolean check() {
            final long now = Minecraft.getSystemTime();
            if (now - lastClickTime < 300) {
                lastClickTime = 0;
                return true;
            }
            lastClickTime = now;
            return false;
        }
    }
}
