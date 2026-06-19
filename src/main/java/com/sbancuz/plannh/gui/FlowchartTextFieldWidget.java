package com.sbancuz.plannh.gui;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widgets.textfield.BaseTextFieldWidget;

public abstract class FlowchartTextFieldWidget extends BaseTextFieldWidget<FlowchartTextFieldWidget>
    implements IFlowchartDraggable {

    protected final FlowchartWidget<?, ?> parent;
    protected final GuiHelper.DoubleClickDetector doubleClick = new GuiHelper.DoubleClickDetector();

    protected boolean isEditing = false;

    public FlowchartTextFieldWidget(FlowchartWidget<?, ?> parent) {
        this.parent = parent;

        background();
        hoverBackground();
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (!doubleClick.check() && !isFocused()) return Result.IGNORE;
        return super.onMousePressed(mouseButton);
    }

    @Override
    public void onFocus(ModularGuiContext context) {
        super.onFocus(context);
        isEditing = true;
    }

    @Override
    public void onRemoveFocus(ModularGuiContext context) {
        super.onRemoveFocus(context);
        isEditing = false;
    }

    @Override
    public void drawMovingState(ModularGuiContext modularGuiContext, float v) {
        parent.drawMovingState(modularGuiContext, v);
    }

    @Override
    public FlowchartWidget<?, ?> getFlowchartParent() {
        return parent;
    }
}
