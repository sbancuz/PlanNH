package com.sbancuz.plannh.gui;

import net.minecraft.client.Minecraft;

import org.apache.commons.lang3.StringUtils;

import com.cleanroommc.modularui.drawable.GuiDraw;

public final class GuiHelper {

    public static final int TICKS_PER_SECOND = 20;

    public static int zq(final float v, final float zoom) {
        return Math.round(v * zoom);
    }

    /**
     * Machine-count display: integral counts stay bare ("4"), fractional keep two decimals. A
     * genuinely nonzero count never collapses to "0" - the machine is wired in, not idle.
     */
    public static String formatCount(final double count) {
        final long rounded = Math.round(count);
        if (Math.abs(count - rounded) < 5e-3 && (rounded != 0 || count == 0)) {
            return String.valueOf(rounded);
        }
        return count > 0 && count < 0.01 ? trimTrailingZeros(String.format("%.5f", count))
            : String.format("%.2f", count);
    }

    public static String trimTrailingZeros(final String s) {
        if (s.indexOf('.') < 0 && s.indexOf(',') < 0) return s;
        return StringUtils.stripEnd(StringUtils.stripEnd(s, "0"), ".,");
    }

    /**
     * Rate display, the single authority for per-second amounts. Not MUI2's NumberFormat: its SI
     * prefixes kick in at 10k and milli-fy trickle rates, and the never-collapse-to-zero rule is
     * not expressible there.
     */
    public static String formatRate(final float rate) {
        // Suffixes and base match NEI's and AE2's ReadableNumberConverter (both "kMGTPE" over 1000),
        // which is what a player reads everywhere else in the pack. G rather than B also keeps a
        // billion apart from the B fluid amounts already use for buckets.
        if (rate >= 1e18f) return String.format("%.1fE", rate / 1e18f);
        if (rate >= 1e15f) return String.format("%.1fP", rate / 1e15f);
        if (rate >= 1e12f) return String.format("%.1fT", rate / 1e12f);
        if (rate >= 1e9f) return String.format("%.1fG", rate / 1e9f);
        if (rate >= 1e6f) return String.format("%.1fM", rate / 1e6f);
        if (rate >= 1000f) return String.format("%.1fk", rate / 1000f);
        if (rate >= 1f) return String.format("%.2f", rate);
        return trimTrailingZeros(String.format("%.5f", rate));
    }

    public static void drawRectBorder(final int x, final int y, final int w, final int h, final int bw,
        final int color) {
        GuiDraw.drawRect(x, y, w, bw, color);
        GuiDraw.drawRect(x, y + h - bw, w, bw, color);
        GuiDraw.drawRect(x, y, bw, h, color);
        GuiDraw.drawRect(x + w - bw, y, bw, h, color);
    }

    private static final int CLOSE_INNER_INSET = 2;
    private static final int CLOSE_INNER_SHRINK = 4;
    private static final int CLOSE_TEXT_Y_OFF = 1;
    private static final int SHADOW_OFF = 1;
    private static final int SHADOW_EXTRA = 2;

    public static void drawCloseButton(final int pw, final int closeW, final int closeMargin, final int color,
        final int textColor) {
        final int bs = closeW;
        final int bx = pw - bs - closeMargin;
        final int by = closeMargin;
        GuiDraw.drawRect(
            bx - SHADOW_OFF,
            by - SHADOW_OFF,
            bs + SHADOW_EXTRA,
            bs + SHADOW_EXTRA,
            PlannhColors.BTN_DELETE_SHADOW.getColor());
        GuiDraw.drawRect(bx, by, bs, bs, color);
        final int inset = CLOSE_INNER_INSET;
        GuiDraw.drawRect(
            bx + inset,
            by + inset,
            bs - CLOSE_INNER_SHRINK,
            bs - CLOSE_INNER_SHRINK,
            PlannhColors.BTN_DELETE_INNER.getColor());
        final int xw = Minecraft.getMinecraft().fontRenderer.getStringWidth("x");
        GuiDraw.drawText("x", bx + (float) bs / 2 - (float) xw / 2, by + CLOSE_TEXT_Y_OFF, 1.0f, textColor, false);
    }

    public static boolean isInsideCloseButton(final int mx, final int my, final int pw, final int closeW,
        final int closeMargin) {
        final int bs = closeW;
        final int bx = pw - bs - closeMargin;
        final int by = closeMargin;
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
