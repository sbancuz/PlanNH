package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;

public class HeaderTextWidget extends FlowchartTextFieldWidget {

    public HeaderTextWidget(FlowchartWidget<?, ?> parent) {
        super(parent);
        background(
            new DynamicDrawable(
                () -> new Rectangle()
                    .color(isEditing ? PlannhColors.NOTE_BORDER_EDIT.getColor() : Color.argb(1f, 1f, 1f, 0))));

        height(20);
        setScale(1.5f);
        expanded();
        handler.getText()
            .add(
                parent.getData()
                    .getHeader());
    }

    @Override
    public boolean canScrollHorizontally() {
        return false;
    }

    @Override
    protected void onTextChanged() {
        parent.getData()
            .setHeader(
                handler.getText()
                    .get(0));
    }
}
