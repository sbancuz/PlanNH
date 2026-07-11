package com.sbancuz.plannh.gui.common;

import java.util.function.IntSupplier;

import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;

public class HeaderTextWidget extends FlowchartTextFieldWidget {

    public HeaderTextWidget(FlowchartWidget<?, ?> parent, IntSupplier bgColor) {
        super(parent);

        height(20);
        setScale(1.5f);
        expanded();
        background(
            new DynamicDrawable(
                () -> new Rectangle().color(isEditing ? bgColor.getAsInt() : Color.argb(1f, 1f, 1f, 0))));

        handler.getText()
            .add(
                parent.getData()
                    .getHeader());
    }

    public HeaderTextWidget(FlowchartWidget<?, ?> parent, int bgColor) {
        this(parent, () -> bgColor);
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
