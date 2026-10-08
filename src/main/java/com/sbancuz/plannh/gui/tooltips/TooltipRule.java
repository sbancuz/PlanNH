package com.sbancuz.plannh.gui.tooltips;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.ITextLine;
import com.cleanroommc.modularui.screen.viewport.GuiContext;

/**
 * The divider between two groups in a tooltip
 */
public final class TooltipRule implements ITextLine {

    private static final String STYLE = String.valueOf(EnumChatFormatting.GRAY) + EnumChatFormatting.STRIKETHROUGH;
    private static final String DASH = "-";

    /** Shorter than any tooltip worth drawing, and one dash is still a rule. */
    private static final int MIN_DASHES = 1;

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public int getHeight(final FontRenderer fr) {
        return fr.FONT_HEIGHT;
    }

    @Override
    public void draw(final GuiContext context, final FontRenderer fr, final float x, final float y, final int color,
        final boolean shadow, final int availableWidth, final int availableHeight) {
        final int per = fr.getStringWidth(STYLE + DASH);
        if (per <= 0) return;
        final int dashes = Math.max(MIN_DASHES, (int) (availableWidth / per));
        fr.drawString(STYLE + DASH.repeat(dashes), (int) x, (int) y, color, shadow);
    }

    @Override
    public Object getHoveringElement(final FontRenderer fr, final int x, final int y) {
        return null;
    }
}
