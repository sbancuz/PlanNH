package com.sbancuz.plannh.gui.summary;

import javax.annotation.Nonnull;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.gui.PlannhColors;

/**
 * A clickable choice row: the active answer is lead-marked; the reason it gives up on hover.
 */
final class ChoiceRow extends SummaryFlow implements Interactable {

    private final Summary.Line.Choice choice;

    ChoiceRow(final Summary.Line.Choice choice) {
        super(GuiAxis.X);
        this.choice = choice;

        fullWidth().coverChildrenHeight(SummaryBody.LINE_H)
            .hoverBackground(new Rectangle().color(PlannhColors.SUMMARY_ROW_HOVER.getColor()))
            .child(
                new TextWidget<>(IKey.str((choice.active() ? "> " : "  ") + choice.displayName()))
                    .paddingLeft(SummaryBody.TEXT_X)
                    .color(
                        choice.active() ? PlannhColors.ACCENT_CYAN2.getColor()
                            : PlannhColors.SUMMARY_TEXT_MUTED.getColor())
                    .textAlign(Alignment.CenterLeft)
                    .fullWidth());

        if (choice.reason() != null) {
            tooltip(
                new RichTooltip().add(
                    choice.reason()
                        .render()));
        }
    }

    @Override
    public @Nonnull Result onMousePressed(final int mouseButton) {
        if (mouseButton != 0) return Result.IGNORE;
        PlanAPI.recordEdit(Plan.getActiveGraph(), () -> {
            Plan.getInstance()
                .getSummary()
                .setExcessChoice(choice.key());
            Plan.getActiveGraph()
                .bumpVersion();
        });
        return Result.SUCCESS;
    }
}
