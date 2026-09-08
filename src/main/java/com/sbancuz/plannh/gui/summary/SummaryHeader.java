package com.sbancuz.plannh.gui.summary;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.data.flowchart.Summary.Line;
import com.sbancuz.plannh.data.flowchart.balancer.Note;
import com.sbancuz.plannh.gui.FlowchartList;
import com.sbancuz.plannh.gui.PlannhColors;

/**
 * The title row of one summary section: the reorder grip, a section-colored highlight bar, the
 * section's name (with a row count for the content sections), and a fold toggle wired straight
 * into the {@link Summary}'s fold bitmask. The CHOICES header carries the budget notes as a hover
 * tooltip.
 */
class SummaryHeader extends ParentWidget<SummaryHeader> {

    public static final int HEADER_H = 18;
    private static final int PAD = 4;

    protected SummaryHeader(final SummaryWidget panel, final Summary data, final Summary.Section section,
        FlowchartList sectionsList) {
        fullWidth().height(HEADER_H)
            .background(new Rectangle().color(PlannhColors.SUMMARY_HEADER_BG.getColor()));

        child(
            SummaryFlow.row()
                .full()
                .childPadding(PAD)
                .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                .child(
                    SummaryFlow.row()
                        .coverChildren()
                        .childPadding(PAD)
                        .child(new FlowchartList.Grip(sectionsList, accentColor(section), textColor(section)))
                        .child(new TextWidget<>(headerTitle(section, data)).color(textColor(section))))
                .child(foldToggle(data, section)));

        child(
            new Widget<>().fullWidth()
                .height(1)
                .background(new Rectangle().color(PlannhColors.SUMMARY_SEPARATOR.getColor())));

        if (section == Summary.Section.CHOICES) {
            tooltipDynamic(tooltip -> {
                for (final Note note : data.getChoiceNotes()) {
                    tooltip.add(note.render())
                        .newLine();
                }
            });
        }
    }

    private static int accentColor(final Summary.Section section) {
        return switch (section) {
            case OUTPUTS -> PlannhColors.SECTION_PRODUCT.getColor();
            case INPUTS -> PlannhColors.SECTION_INPUT.getColor();
            case PROPERTIES, MACHINE_COUNTS -> PlannhColors.SECTION_OPS.getColor();
            case CHOICES -> PlannhColors.SECTION_CHOICE.getColor();
            case MESSAGES -> PlannhColors.SECTION_WARN.getColor();
            case HELP -> PlannhColors.SECTION_FLUID_OUT.getColor();
            default -> PlannhColors.SECTION_CHOICE.getColor();
        };
    }

    private static int textColor(final Summary.Section section) {
        return switch (section) {
            case OUTPUTS -> PlannhColors.ACCENT_AMBER.getColor();
            case INPUTS -> PlannhColors.ACCENT_GREEN2.getColor();
            case PROPERTIES, MACHINE_COUNTS -> PlannhColors.ACCENT_BLUE.getColor();
            case CHOICES -> PlannhColors.ACCENT_CYAN2.getColor();
            case MESSAGES -> PlannhColors.ACCENT_YELLOW.getColor();
            case HELP -> PlannhColors.TEXT_LIGHT.getColor();
            default -> PlannhColors.TEXT_WHITE.getColor();
        };
    }

    static CycleButtonWidget foldToggle(final Summary data, final Summary.Section section) {
        return new CycleButtonWidget().stateCount(2)
            .size(HEADER_H, HEADER_H)
            .stateOverlay(true, IKey.str("V"))
            .stateOverlay(false, IKey.str("^"))
            .value(new BoolValue.Dynamic(() -> data.isSummaryFold(section), val -> data.setSummaryFold(section, val)));
    }

    private static IKey headerTitle(final Summary.Section section, final Summary data) {
        final IKey title = IKey.lang(section.titleKey());
        if (section == Summary.Section.HELP) return title;
        return IKey.dynamicKey(() -> IKey.comp(title, IKey.str(" (" + count(section, data) + ")")));
    }

    private static int count(final Summary.Section section, final Summary data) {
        if (section == Summary.Section.CHOICES) {
            return (int) data.lines(section)
                .stream()
                .filter(Line.Choice.class::isInstance)
                .count();
        }
        return data.lineCount(section);
    }

}
