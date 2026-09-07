package com.sbancuz.plannh.gui.summary;

import com.cleanroommc.modularui.widget.ParentWidget;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Summary;
import com.sbancuz.plannh.gui.FlowchartList;

/**
 * One section of the summary panel: the accent-bar title row with its fold toggle, plus the row
 * body. The whole section hides itself when it has nothing to show; the body alone hides when the
 * section is folded away, so the header stays clickable.
 */
class SummarySection extends ParentWidget<SummarySection> {

    private static final int INNER_GAP = 3;

    private final Summary data;
    private final Summary.Section section;

    SummarySection(final SummaryWidget panel, final Summary.Section section, FlowchartList sectionsList) {
        this.data = Plan.getInstance()
            .getSummary();
        this.section = section;

        fullWidth().coverChildrenHeight()
            .setEnabledIf(_ -> data.lineCount(section) > 0);

        child(
            SummaryFlow.col()
                .fullWidth()
                .coverChildrenHeight()
                .childPadding(INNER_GAP)
                .collapseDisabledChild()
                .child(new SummaryHeader(panel, data, section, sectionsList))
                .child(new SummaryBody(panel, data, section).setEnabledIf(_ -> !data.isSummaryFold(section))));
    }

    Summary.Section section() {
        return section;
    }
}
