package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widgets.ListWidget;

public abstract class ClampedListWidget<I extends IWidget, W extends ClampedListWidget<I, W>> extends ListWidget<I, W> {

    @Override
    public boolean postLayoutWidgets() {
        final boolean done = super.postLayoutWidgets();
        getScrollData().clamp(getScrollArea());
        return done;
    }
}
