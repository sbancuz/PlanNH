package com.sbancuz.plannh.gui;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widget.AbstractWidget;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.GraphData;

import lombok.Getter;

public abstract class FlowchartWidget<T extends ParentWidget<T>, D extends GraphData> extends ParentWidget<T>
    implements Interactable, IFlowchartDraggable {

    @Getter
    protected final D data;
    @Getter
    protected final CanvasWidget canvas;
    private boolean moving = false;
    private int dragStartMouseX, dragStartMouseY;
    protected int dragOffsetX, dragOffsetY;
    private int dragStartX, dragStartY;
    private String dragEditToken;
    protected List<FlowchartWidget<?, ?>> dragStartIntersect;

    protected FlowchartWidget(CanvasWidget canvas, D data) {
        this.canvas = canvas;
        this.data = data;
        pos(data.getX(), data.getY());
        canvas.getFlowchartWidgets()
            .put(data.getId(), this);
    }

    public FlowchartWidget<?, ?> getFlowchartParent() {
        return this;
    }

    @Override
    public void drawMovingState(ModularGuiContext context, float partialTicks) {}

    @Override
    public boolean onDragStart(int mouseButton) {
        return onDragStartWithOffset(mouseButton, 0, 0);
    }

    public boolean onDragStartWithOffset(int mouseButton, int x, int y) {
        if (mouseButton == 0 && canvas.isMouseInsideCanvas()) {
            ModularGuiContext context = getContext();
            dragEditToken = PlanAPI.undoHistory()
                .beginEdit(canvas.getGraph());
            dragStartX = data.getX();
            dragStartY = data.getY();
            dragOffsetX = x + context.getMouseX();
            dragOffsetY = y + context.getMouseY();
            dragStartMouseX = context.getAbsMouseX();
            dragStartMouseY = context.getAbsMouseY();
            dragStartIntersect = canvas.getFlowchartWidgets()
                .values()
                .stream()
                .filter(
                    widget -> widget.getArea()
                        .intersects(getArea()))
                .toList();
            return true;
        }
        return false;
    }

    protected void handleDrag(boolean successful) {
        if (!successful) {
            data.setX(dragStartX);
            data.setY(dragStartY);
            reposition();
        } else {
            if (canvas.getGraph()
                .isSnapToGrid()) {
                data.setX((int) (Math.round((double) data.getX() / CanvasWidget.GRID_SIZE) * CanvasWidget.GRID_SIZE));
                data.setY((int) (Math.round((double) data.getY() / CanvasWidget.GRID_SIZE) * CanvasWidget.GRID_SIZE));
                reposition();
            }
        }
    }

    @Override
    public void onDragEnd(boolean successful) {
        handleDrag(successful);

        PlanAPI.undoHistory()
            .commitEdit(dragEditToken, canvas.getGraph());
        dragEditToken = null;
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

    @Override
    public boolean canDropHere(int x, int y, @Nullable IWidget widget) {
        // we can only intersect with ourselves or groups
        return canvas.isMouseInsideCanvas() && canvas.getFlowchartWidgets()
            .values()
            .stream()
            .filter(f -> !dragStartIntersect.contains(f))
            .map(AbstractWidget::getArea)
            .noneMatch(area -> area.intersects(getArea()));
    }

    protected void reposition() {
        pos(data.getX(), data.getY());
    }

}
