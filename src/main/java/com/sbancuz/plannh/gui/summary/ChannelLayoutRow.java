package com.sbancuz.plannh.gui.summary;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.sbancuz.plannh.data.channels.ChannelReport;
import com.sbancuz.plannh.data.channels.ChannelSolver;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.gui.PlannhColors;

/** One channel: its dye and catalysts in check order. In color mode, a machine with a line per bus. */
final class ChannelLayoutRow extends ChannelRow {

    private static final int CHIP = 7;

    ChannelLayoutRow(final Summary.Line.ChannelLayout g) {
        super(GuiAxis.Y);
        final boolean color = g.mode() == ChannelSolver.Mode.COLOR;
        final String title = tr(color ? "machine" : "channel", g.index());

        fullWidth().coverChildrenHeight()
            .hoverBackground(new Rectangle().color(PlannhColors.SUMMARY_ROW_HOVER.getColor()));
        if (color) {
            child(line(null, title, PlannhColors.TEXT_WHITE.getColor()));
            for (int i = 0; i < g.catalysts()
                .size(); i++) {
                final ChannelReport.Dye dye = g.dyes()
                    .get(i);
                child(
                    line(
                        dye,
                        dye.name() + ": "
                            + catalysts(
                                g.catalysts()
                                    .get(i)),
                        PlannhColors.SUMMARY_TEXT.getColor()).paddingLeft(SummaryBody.TEXT_X * 2));
            }
        } else {
            final String separator = g.mode() == ChannelSolver.Mode.NONE ? ", " : " > ";
            final StringBuilder sb = new StringBuilder(title).append(": ");
            for (int i = 0; i < g.catalysts()
                .size(); i++) {
                if (i > 0) sb.append(separator);
                sb.append(
                    catalysts(
                        g.catalysts()
                            .get(i)));
            }
            child(
                line(
                    g.dyes()
                        .getFirst(),
                    sb.toString(),
                    PlannhColors.SUMMARY_TEXT.getColor()));
        }

        tooltipStatic(t -> {
            title(t, title);
            // What to dye: each bus in color mode, else the whole group if other channels share the machine
            if (color) body(t, tr("dye.buses"));
            else if (g.of() > 1) body(
                t,
                tr(
                    "dye.group",
                    g.dyes()
                        .getFirst()
                        .name()));
            else body(t, tr("dye.none"));
            body(t, parts(g.parts()));
            gap(t);
            heading(t, tr("recipes"));
            for (final String r : g.recipes()) item(t, r);
        });
    }

    /** A dye chip (none when {@code dye} is null) and text that wraps in the space left. */
    private static SummaryFlow line(final ChannelReport.Dye dye, final String text, final int color) {
        final SummaryFlow row = SummaryFlow.row();
        row.fullWidth()
            .coverChildrenHeight(SummaryBody.LINE_H)
            .paddingLeft(SummaryBody.TEXT_X)
            .childPadding(GAP)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        if (dye != null) row.child(
            new Widget<>().size(CHIP, CHIP)
                .background(new Rectangle().color(0xFF000000 | dye.rgb())));
        row.child(
            new TextWidget<>(IKey.str(text)).color(color)
                .textAlign(Alignment.CenterLeft)
                .expanded());
        return row;
    }
}
