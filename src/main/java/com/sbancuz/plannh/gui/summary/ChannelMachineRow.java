package com.sbancuz.plannh.gui.summary;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.sbancuz.plannh.data.channels.ChannelSolver;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.gui.PlannhColors;

/** A machine pool's heading: its name, then how many channels, machines and input blocks it needs. */
final class ChannelMachineRow extends ChannelRow {

    ChannelMachineRow(final Summary.Line.ChannelMachine m) {
        super(GuiAxis.Y);
        final String name = m.group() != null ? tr("group", m.group(), m.machine()) : m.machine();
        final String machines = plural("stat.machines", m.machines());
        final String blocks = plural(
            "stat.blocks",
            m.parts()
                .blocks());
        // In color mode every channel is a machine of its own
        final String stats = m.mode() == ChannelSolver.Mode.COLOR ? String.join("  ·  ", machines, blocks)
            : String.join("  ·  ", plural("stat.channels", m.channels()), machines, blocks);
        final boolean overCapacity = m.capacity() > 0 && m.machines() > m.capacity();

        fullWidth().coverChildrenHeight()
            .paddingLeft(SummaryBody.TEXT_X)
            .paddingTop(GAP)
            .hoverBackground(new Rectangle().color(PlannhColors.SUMMARY_ROW_HOVER.getColor()))
            .child(text(name, PlannhColors.ACCENT_BLUE.getColor()))
            .child(
                text(
                    stats,
                    overCapacity ? PlannhColors.ACCENT_AMBER.getColor() : PlannhColors.SUMMARY_TEXT_MUTED.getColor()));

        tooltipStatic(t -> {
            title(t, name);
            body(t, parts(m.parts()));
            body(t, tr("dedicated", m.dedicated()));
            if (overCapacity) warn(t, tr("capacity", m.machines(), m.capacity()));
            if (!m.channelsMinimal()) warn(t, tr("budget.channels"));
            else if (!m.blocksMinimal()) warn(t, tr("budget.blocks"));
            gap(t);
            heading(t, tr(layout(m.mode())));
            body(t, tr(layout(m.mode()) + ".help"));
        });
    }

    private static TextWidget<?> text(final String text, final int color) {
        return new TextWidget<>(IKey.str(text)).color(color)
            .textAlign(Alignment.CenterLeft)
            .fullWidth();
    }
}
