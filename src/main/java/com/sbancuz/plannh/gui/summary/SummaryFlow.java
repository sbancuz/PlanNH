package com.sbancuz.plannh.gui.summary;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.widgets.layout.Flow;

/**
 * A {@link Flow} that lets clicks pass through to widgets behind it in the hover list.
 * Without this, {@link AbstractParentWidget#canClickThrough()}
 * returns {@code false} whenever the flow has a background or hover background, which breaks
 * {@link IDraggable} on any parent that wraps its content in
 * a backed {@link Flow}.
 */
class SummaryFlow extends Flow {

    SummaryFlow(final GuiAxis axis) {
        super(axis);
    }

    public static SummaryFlow col() {
        return new SummaryFlow(GuiAxis.Y);
    }

    public static SummaryFlow column() {
        return new SummaryFlow(GuiAxis.Y);
    }

    public static SummaryFlow row() {
        return new SummaryFlow(GuiAxis.X);
    }

    @Override
    public boolean canClickThrough() {
        return true;
    }
}
