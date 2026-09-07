package com.sbancuz.plannh.gui.summary;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.sbancuz.plannh.gui.FlowchartFlow;
import com.sbancuz.plannh.gui.FlowchartWidget;
import com.sbancuz.plannh.gui.PlannhColors;

final class TextRow extends FlowchartFlow {

    TextRow(final FlowchartWidget<?, ?> panel, final IKey text, final int color) {
        super(GuiAxis.X, panel);
        fullWidth().coverChildrenHeight(SummaryBody.LINE_H)
            .paddingLeft(SummaryBody.TEXT_X)
            .hoverBackground(new Rectangle().color(PlannhColors.SUMMARY_ROW_HOVER.getColor()))
            .child(
                new TextWidget<>(text).color(color)
                    .textAlign(Alignment.CenterLeft)
                    .fullWidth());
    }
}
