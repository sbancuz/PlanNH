package com.sbancuz.plannh.gui;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widgets.ButtonWidget;

public class CloseButtonWidget extends ButtonWidget<CloseButtonWidget> {

    private final FlowchartWidget<?, ?> parent;

    public CloseButtonWidget(FlowchartWidget<?, ?> parent) {
        this.parent = parent;
        background(
            new Rectangle().color(PlannhColors.NOTE_CLOSE_BG.getColor()),
            new Rectangle().color(Color.BLACK.main)
                .asIcon()
                .size(8));
        child(
            IKey.str("x")
                .color(Color.WHITE.main)
                .asWidget()
                .center());
        size(12);
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        parent.getCanvas()
            .remove(parent);
        parent.removeFromGraph();
        return Result.SUCCESS;
    }
}
