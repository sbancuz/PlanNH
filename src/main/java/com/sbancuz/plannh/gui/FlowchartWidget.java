package com.sbancuz.plannh.gui;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Area;

public abstract class FlowchartWidget<T extends ParentWidget<T>> extends ParentWidget<T>
    implements Interactable, IDraggable {

    protected boolean moving = false;
    protected int dragStartMouseX, dragStartMouseY;
    protected int dragStartX, dragStartY;
    protected int x, y;

    protected final CanvasWidget canvas;
    protected final GuiHelper.DoubleClickDetector doubleClick = new GuiHelper.DoubleClickDetector();

    protected FlowchartWidget(CanvasWidget canvas) {
        this.canvas = canvas;
    }

    @Override
    public void drawMovingState(ModularGuiContext context, float partialTicks) {}

    @Override
    public boolean onDragStart(int mouseButton) {
        if (mouseButton == 0) {
            dragStartX = x;
            dragStartY = y;
            dragStartMouseX = getContext().getAbsMouseX();
            dragStartMouseY = getContext().getAbsMouseY();
            return true;
        }
        return false;
    }

    @Override
    public void onDragEnd(boolean successful) {
        if (!successful) pos(dragStartX, dragStartY);
    }

    @Override
    public void onDrag(int mouseButton, long timeSinceLastClick) {
        final int dx = getContext().getAbsMouseX() - dragStartMouseX;
        final int dy = getContext().getAbsMouseY() - dragStartMouseY;
        final float z = canvas.getZoom();
        x = dragStartX + Math.round(dx / z);
        y = dragStartY + Math.round(dy / z);
        pos(x, y);
    }

    @Override
    public @Nullable Area getMovingArea() {
        return null;
    }

    @Override
    public boolean isMoving() {
        return moving;
    }

    @Override
    public void setMoving(boolean moving) {
        this.moving = moving;
    }
}
