package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;

public class HeaderTextWidget extends FlowchartTextFieldWidget {

    public HeaderTextWidget(FlowchartWidget<?, ?> parent, int bgColor) {
        super(parent);

        height(20);
        setScale(1.5f);
        expanded();
        background(new DynamicDrawable(() -> new Rectangle().color(isEditing ? bgColor : Color.argb(1f, 1f, 1f, 0))));

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
