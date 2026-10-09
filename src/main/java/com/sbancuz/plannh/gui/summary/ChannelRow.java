package com.sbancuz.plannh.gui.summary;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.sbancuz.plannh.data.channels.ChannelSolver;

/** A row of the channels section, with the text and tooltip styling the rows share. */
abstract class ChannelRow extends SummaryFlow {

    static final String KEY = "plannh.summary.channels.";
    static final int GAP = 3;

    ChannelRow(final GuiAxis axis) {
        super(axis);
    }

    protected static String tr(final String key, final Object... args) {
        return StatCollector.translateToLocalFormatted(KEY + key, args);
    }

    /** The lang file holds a {@code .one} and a {@code .many} form. */
    protected static String plural(final String key, final int n) {
        return tr(key + (n == 1 ? ".one" : ".many"), n);
    }

    protected static String layout(final ChannelSolver.Mode mode) {
        return "layout." + switch (mode) {
            case NONE -> "separate";
            case CIRCUIT -> "circuit";
            case COLOR -> "color";
        };
    }

    protected static String catalysts(final String name) {
        return name.isEmpty() ? tr("circuitless") : name;
    }

    protected static String parts(final ChannelSolver.Parts p) {
        return tr("parts", p.buses(), p.quad(), p.normal());
    }

    protected static void title(final RichTooltip t, final String text) {
        t.addLine(
            IKey.str(text)
                .style(EnumChatFormatting.AQUA, EnumChatFormatting.BOLD));
    }

    protected static void heading(final RichTooltip t, final String text) {
        t.addLine(
            IKey.str(text)
                .style(EnumChatFormatting.WHITE, EnumChatFormatting.UNDERLINE));
    }

    protected static void body(final RichTooltip t, final String text) {
        t.addLine(
            IKey.str(text)
                .style(EnumChatFormatting.GRAY));
    }

    protected static void warn(final RichTooltip t, final String text) {
        t.addLine(
            IKey.str(text)
                .style(EnumChatFormatting.GOLD));
    }

    protected static void item(final RichTooltip t, final String text) {
        t.addLine(
            IKey.comp(
                IKey.str("• ")
                    .style(EnumChatFormatting.DARK_GRAY),
                IKey.str(text)
                    .style(EnumChatFormatting.GRAY)));
    }

    /** A named choice and what it means; the one in effect is marked. */
    protected static void option(final RichTooltip t, final String name, final String help, final boolean current) {
        t.addLine(
            current ? IKey.str("▶ " + name)
                .style(EnumChatFormatting.GREEN, EnumChatFormatting.BOLD)
                : IKey.str(name)
                    .style(EnumChatFormatting.WHITE));
        t.addLine(
            IKey.str(help)
                .style(EnumChatFormatting.GRAY));
    }

    protected static void footer(final RichTooltip t, final String text) {
        t.addLine(
            IKey.str(text)
                .style(EnumChatFormatting.DARK_GRAY, EnumChatFormatting.ITALIC));
    }

    protected static void gap(final RichTooltip t) {
        t.spaceLine(GAP);
    }
}
