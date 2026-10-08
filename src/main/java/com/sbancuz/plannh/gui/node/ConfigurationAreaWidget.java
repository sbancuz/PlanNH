package com.sbancuz.plannh.gui.node;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.sbancuz.plannh.gui.ClampedListWidget;

class ConfigurationAreaWidget extends ClampedListWidget<IWidget, ConfigurationAreaWidget> {

    /** How many rows the region shows before the screen bound takes over as the limit. */
    // TODO: Config pass
    private static final int VISIBLE_ROWS = 10;
    private static final int SCROLLBAR_GAP = 4;

    private int avgRow;

    ConfigurationAreaWidget(final NodeWidget node) {
        name("node.config");
        showScrollShadows(false);

        fullWidth().crossAxisAlignment(Alignment.CrossAxis.START)
            .paddingRight(SCROLLBAR_GAP)
            .scrollDirection(new VerticalScrollData())
            .maxSize(() -> avgRow == 0 ? Integer.MAX_VALUE : VISIBLE_ROWS * avgRow);

        child(new ThroughputFold(node));
        child(new PropertiesFold(node));
        child(new TargetFold(node));
        child(new SettingsFold(node));
    }

    @Override
    public boolean postLayoutWidgets() {
        final boolean done = super.postLayoutWidgets();
        final int avg = averageRowHeight();
        if (avg != avgRow) {
            avgRow = avg;
            scheduleResize();
        }
        return done;
    }

    @Override
    public boolean canClickThrough() {
        return true;
    }

    /**
     * The tallest row any open fold is showing, which is the unit the region's height is counted in.
     */
    private int averageRowHeight() {
        int height = 0, count = 0;
        for (final IWidget section : getChildren()) {
            if (!section.isEnabled() || !(section instanceof final ParentWidget<?> fold)) continue;
            for (final IWidget row : fold.getChildren()) {
                final Area a = row.getArea();
                height+= a.height + a.getMargin().getTotal(GuiAxis.Y);
                count += 1;
            }
        }
        return height / count;
    }
}
