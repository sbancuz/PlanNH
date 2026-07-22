package com.sbancuz.plannh.gui;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.textfield.BaseTextFieldWidget;
import com.sbancuz.plannh.api.PlanAPI;

import it.unimi.dsi.fastutil.Pair;

public abstract class FlowchartTextFieldWidget extends BaseTextFieldWidget<FlowchartTextFieldWidget>
    implements IFlowchartDraggable {

    protected final FlowchartWidget<?, ?> parent;
    protected final GuiHelper.DoubleClickDetector doubleClick = new GuiHelper.DoubleClickDetector();

    protected boolean isEditing = false;
    // Text mutates the model per keystroke, so the whole focus session is one undo step.
    private String editToken;

    public FlowchartTextFieldWidget(FlowchartWidget<?, ?> parent) {
        this.parent = parent;

        background();
        hoverBackground();
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (!parent.getCanvas()
            .isMouseInsideCanvas()) return Result.STOP;
        if (!doubleClick.check() && !isFocused()) return Result.IGNORE;
        return super.onMousePressed(mouseButton);
    }

    @Override
    public void onFocus(ModularGuiContext context) {
        super.onFocus(context);
        isEditing = true;
        editToken = PlanAPI.undoHistory()
            .beginEdit(
                parent.getCanvas()
                    .getGraph());
    }

    @Override
    public void onRemoveFocus(ModularGuiContext context) {
        super.onRemoveFocus(context);
        isEditing = false;
        PlanAPI.undoHistory()
            .commitEdit(
                editToken,
                parent.getCanvas()
                    .getGraph());
        editToken = null;
    }

    @Override
    public FlowchartWidget<?, ?> getFlowchartParent() {
        return parent;
    }

    @Override
    public Pair<Integer, Integer> dragOffset() {
        int x = getArea().rx;
        int y = getArea().ry;
        IWidget parentWidget = getParent();
        while (parentWidget instanceof ParentWidget<?>parentWidgetActual && parentWidget != parent) {
            x += parentWidget.getArea().rx;
            y += parentWidget.getArea().ry;
            parentWidget = parentWidgetActual.getParent();
        }
        return Pair.of(x, y);
    }
}
