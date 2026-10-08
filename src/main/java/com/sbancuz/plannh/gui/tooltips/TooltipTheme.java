package com.sbancuz.plannh.gui.tooltips;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;

public final class TooltipTheme {

    /**
     * A tooltip line can only carry the 16 legacy §-codes.
     *
     * <p>
     * {@code RichText} flattens a line to one formatted string and {@code FormattingState} has no
     * ARGB field, so {@code IKey.color(int)} is dropped - it is read only by {@code StyledText.draw},
     * the widget path. Arbitrary colour needs a hand-written {@code ITextLine} instead.
     */
    public enum Role {

        /** A label, and any value with nothing further to say. */
        BODY(EnumChatFormatting.GRAY),
        /** A label the reader is meant to read first. */
        LABEL(EnumChatFormatting.WHITE),
        /** A plain count, duration or name: the value of a thing, stated. */
        IDENTITY(EnumChatFormatting.WHITE),
        /** Anything per unit of time. */
        RATE(EnumChatFormatting.AQUA),
        /** Energy, which the pack marks in its own colour wherever it turns up. */
        POWER(EnumChatFormatting.YELLOW),
        /** A number the reader can change, so it reads as a setting rather than a fact. */
        TUNABLE(EnumChatFormatting.GOLD),
        /** On the machine but not read: a target that is not picked, a setting left off. */
        MUTED(EnumChatFormatting.DARK_GRAY),
        /**
         * The one row that is actually in use.
         *
         * <p>
         * Green, not the pack's accent cyan: that hue is {@link #RATE}'s, and a selected marker drawn
         * in the same colour as the rates beside it does not read as selected.
         */
        ACCENT(EnumChatFormatting.GREEN);

        private final EnumChatFormatting code;

        Role(final EnumChatFormatting code) {
            this.code = code;
        }

        /** The §-code this role is written in. */
        public EnumChatFormatting code() {
            return code;
        }
    }

    private TooltipTheme() {}

    public static IKey key(final Role role, final String text) {
        return IKey.str(text)
            .style(role.code());
    }
}
