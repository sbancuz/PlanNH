package com.sbancuz.plannh.gui.summary;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.sbancuz.plannh.data.channels.ChannelReport;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.gui.PlannhColors;

/** A count of one kind of finding for a machine pool, each listed on hover. */
final class ChannelFindingsRow extends ChannelRow {

    ChannelFindingsRow(final Summary.Line.ChannelFindings f) {
        super(GuiAxis.X);
        final String kind = switch (f.kind()) {
            case CONFLICT -> "conflicts";
            case TOLERATED -> "tolerated";
            case INHERENT -> "inherent";
        };
        final int color = switch (f.kind()) {
            case CONFLICT -> PlannhColors.ACCENT_AMBER.getColor();
            case TOLERATED -> PlannhColors.SUMMARY_TEXT_MUTED.getColor();
            case INHERENT -> PlannhColors.ACCENT_RED.getColor();
        };

        fullWidth().coverChildrenHeight(SummaryBody.LINE_H)
            .paddingLeft(SummaryBody.TEXT_X)
            .hoverBackground(new Rectangle().color(PlannhColors.SUMMARY_ROW_HOVER.getColor()))
            .child(
                new TextWidget<>(
                    IKey.str(
                        plural(
                            kind,
                            f.notes()
                                .size()))).color(color)
                                    .textAlign(Alignment.CenterLeft)
                                    .fullWidth());

        tooltipStatic(t -> {
            title(t, tr(kind + ".title"));
            body(t, tr(kind + ".help"));
            gap(t);
            for (final Summary.ChannelNote n : f.notes()) item(t, note(f.kind(), n));
        });
    }

    private static String note(final ChannelReport.Kind kind, final Summary.ChannelNote n) {
        final String plan = n.plan() ? " " + tr("note.plan") : "";
        return switch (kind) {
            case CONFLICT -> n.victim() == null ? tr("note.passive", n.culprit(), n.needs())
                : tr("note.conflict", n.victim(), n.culprit(), n.needs()) + plan;
            case TOLERATED -> tr(
                "note.tolerated",
                n.victim(),
                n.scale(),
                n.eut(),
                String.format("%.3g", n.timeRatio()));
            case INHERENT -> tr("note.inherent", n.victim(), n.culprit()) + plan;
        };
    }
}
