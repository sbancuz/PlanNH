package com.sbancuz.plannh.gui.node;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.sbancuz.plannh.gui.ClampedListWidget;

class ConfigurationAreaWidget extends ClampedListWidget<IWidget, ConfigurationAreaWidget> {

    /** How many rows the region shows before the screen bound takes over as the limit. */
    private static final int VISIBLE_ROWS = 8;
    private static final int SCROLLBAR_GAP = 4;

    private int tallestRow;

    ConfigurationAreaWidget(final NodeWidget node) {
        showScrollShadows(false);

        fullWidth().crossAxisAlignment(Alignment.CrossAxis.START)
            .paddingRight(SCROLLBAR_GAP)
            .scrollDirection(new VerticalScrollData())
            .maxSize(() -> tallestRow == 0 ? Integer.MAX_VALUE : VISIBLE_ROWS * tallestRow);

        child(new ThroughputFold(node));
        child(new PropertiesFold(node));
        child(new TargetFold(node));
        child(new SettingsFold(node));
    }

    @Override
    public boolean postLayoutWidgets() {
        final boolean done = super.postLayoutWidgets();
        final int tallest = tallestRow();
        if (tallest != tallestRow) {
            tallestRow = tallest;
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
    private int tallestRow() {
        int tallest = 0;
        for (final IWidget section : getChildren()) {
            if (!section.isEnabled() || !(section instanceof final ParentWidget<?> fold)) continue;
            for (final IWidget row : fold.getChildren()) {
                tallest = Math.max(
                    tallest,
                    row.getArea()
                        .getSize(GuiAxis.Y)
                        + row.getArea()
                            .getMargin()
                            .getTotal(GuiAxis.Y));
            }
        }
        return tallest;
    }
}
