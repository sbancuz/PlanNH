package com.sbancuz.plannh.gui.summary;

import java.util.Locale;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.gui.GuiHelper;
import com.sbancuz.plannh.gui.PlannhColors;

/**
 * The chart-wide totals: total operations plus the time one full cycle takes. The model only
 * supplies the numbers; the sentence is built and localized here, in the panel, in the shape
 * the current mode wants (cycles: "Time: 240t (12.0s/cycle)", throughput: "Cycle: 12.0s").
 */
final class TotalsRow extends SummaryFlow {

    TotalsRow(final Summary.Line.Totals totals, final Summary.Mode mode) {
        super(GuiAxis.X);
        final String ops = GuiHelper.formatCount(totals.operations());
        final String sec = String
            .format(Locale.ROOT, "%.2f", (double) totals.durationTicks() / GuiHelper.TICKS_PER_SECOND);
        final IKey text = mode == Summary.Mode.THROUGHPUT ? IKey.lang("plannh.summary.totals.throughput", ops, sec)
            : IKey.lang("plannh.summary.totals.cycles", ops, totals.durationTicks(), sec);
        fullWidth().coverChildrenHeight(SummaryBody.LINE_H)
            .paddingLeft(SummaryBody.TEXT_X)
            .hoverBackground(new Rectangle().color(PlannhColors.SUMMARY_ROW_HOVER.getColor()))
            .child(
                new TextWidget<>(text).color(PlannhColors.ACCENT_BLUE.getColor())
                    .textAlign(Alignment.CenterLeft)
                    .fullWidth());
    }
}
