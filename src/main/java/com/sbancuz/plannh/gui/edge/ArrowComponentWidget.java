package com.sbancuz.plannh.gui.edge;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.widget.Widget;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.gui.node.PortWidget;

public abstract class ArrowComponentWidget extends Widget<ArrowComponentWidget> implements Interactable {

    private final ArrowWidget parent;

    protected ArrowComponentWidget(ArrowWidget parent) {
        this.parent = parent;
        addTooltipLine("Click arrow to remove");
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        PlanAPI.recordEdit(
            parent.getCanvas()
                .getGraph(),
            parent::removeFromGraph);
        return Result.SUCCESS;
    }

    @Override
    public boolean canHover() {
        return PortWidget.arrowWidgetInCreation == null;
    }

    public abstract void configure(int outerColor, int innerColor, int[]... coords);
}
