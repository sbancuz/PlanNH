package com.sbancuz.plannh.gui;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.sbancuz.plannh.data.flowchart.GraphData;

import lombok.Getter;

public abstract class FlowchartWidget<T extends ParentWidget<T>, D extends GraphData> extends ParentWidget<T>
    implements Interactable, IDraggable {

    @Getter
    protected final D data;
    @Getter
    protected final CanvasWidget canvas;
    private boolean moving = false;
    private int dragStartMouseX, dragStartMouseY;
    private int dragStartX, dragStartY;

    protected FlowchartWidget(CanvasWidget canvas, D data) {
        this.canvas = canvas;
        this.data = data;
        pos(data.getX(), data.getY());
    }

    @Override
    public void drawMovingState(ModularGuiContext context, float partialTicks) {}

    @Override
    public boolean onDragStart(int mouseButton) {
        if (mouseButton == 0) {
            dragStartX = data.getX();
            dragStartY = data.getY();
            dragStartMouseX = getContext().getAbsMouseX();
            dragStartMouseY = getContext().getAbsMouseY();
            return true;
        }
        return false;
    }

    @Override
    public void onDragEnd(boolean successful) {
        if (!successful) {
            data.setX(dragStartX);
            data.setY(dragStartY);
            reposition();
        } else if (canvas.getGraph()
            .isSnapToGrid()) {
                data.setX((int) (Math.round((double) data.getX() / CanvasWidget.GRID_SIZE) * CanvasWidget.GRID_SIZE));
                data.setY((int) (Math.round((double) data.getY() / CanvasWidget.GRID_SIZE) * CanvasWidget.GRID_SIZE));
                reposition();
            }
    }

    @Override
    public void onDrag(int mouseButton, long timeSinceLastClick) {
        final int dx = getContext().getAbsMouseX() - dragStartMouseX;
        final int dy = getContext().getAbsMouseY() - dragStartMouseY;
        final float z = canvas.getGraph()
            .getZoom();
        data.setX(dragStartX + Math.round(dx / z));
        data.setY(dragStartY + Math.round(dy / z));
        reposition();
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

    public abstract void removeFromGraph();

    protected void reposition() {
        pos(data.getX(), data.getY());
    }
}
