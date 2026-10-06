package com.sbancuz.plannh.gui.common;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

/** The node tooltip's visual language: a gray body, white labels, a colour per kind of value. */
public final class TooltipStyle {

    public static final EnumChatFormatting BODY = EnumChatFormatting.GRAY;
    public static final EnumChatFormatting TUNABLE = EnumChatFormatting.GOLD;
    public static final EnumChatFormatting IDENTITY = EnumChatFormatting.WHITE;
    public static final EnumChatFormatting RATE = EnumChatFormatting.AQUA;
    public static final EnumChatFormatting POWER = EnumChatFormatting.YELLOW;

    private static final String COLON = ": ";
    private static final String TAB = "   ";

    private static final String RULE = String.valueOf(EnumChatFormatting.DARK_GRAY) + EnumChatFormatting.STRIKETHROUGH
        + "-".repeat(41);

    private TooltipStyle() {}

    public static String rule() {
        return RULE;
    }

    public static String header(final String key) {
        return BODY + StatCollector.translateToLocal(key) + COLON;
    }

    public static String plain(final String label, final EnumChatFormatting colour, final String value) {
        return BODY + label + COLON + colour + value + BODY;
    }

    public static String entry(final String label, final EnumChatFormatting colour, final String value) {
        return BODY + TAB + plain(label, colour, value);
    }

    public static String flag(final String label) {
        return BODY + TAB + label;
    }
}
