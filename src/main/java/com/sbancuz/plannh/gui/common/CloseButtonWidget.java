package com.sbancuz.plannh.gui.common;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.sbancuz.plannh.gui.PlannhColors;

public class CloseButtonWidget extends ButtonWidget<CloseButtonWidget> {

    private final FlowchartWidget<?, ?> parent;

    public CloseButtonWidget(FlowchartWidget<?, ?> parent) {
        this.parent = parent;
        background(
            new Rectangle().color(PlannhColors.NOTE_CLOSE_BG.getColor()),
            new Rectangle().color(Color.BLACK.main)
                .asIcon()
                .size(8));
        overlay(
            IKey.str("x")
                .color(Color.WHITE.main));
        size(12);
        marginRight(2);
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (parent.getCanvas()
            .isMouseInsideCanvas()) {
            ((ParentWidget<?>) parent.getParent()).remove(parent);
            parent.removeFromGraph();
        }
        return Result.SUCCESS;
    }
}
