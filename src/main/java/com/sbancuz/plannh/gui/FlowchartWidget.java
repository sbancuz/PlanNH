package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widget.AbstractWidget;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Note;
import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.sbancuz.plannh.data.flowchart.GraphData;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

public abstract class FlowchartWidget<T extends ParentWidget<T>, D extends GraphData> extends ParentWidget<T>
    implements Interactable, IDraggable {

    @Getter
    protected final D data;
    @Getter
    protected final CanvasWidget canvas;
    private boolean moving = false;
    private int dragStartMouseX, dragStartMouseY;
    private int dragStartX, dragStartY;
    protected Map<UUID, GraphData> dataContainer;
    private List<FlowchartWidget<?, ?>> dragStartIntersect;

    protected FlowchartWidget(CanvasWidget canvas, D data) {
        this.canvas = canvas;
        this.data = data;
        dataContainer = (Map<UUID, GraphData>) getDefaultContainer();
        pos(data.getX(), data.getY());
        canvas.getFlowchartWidgets().put(data.getId(), this);
    }

    @Override
    public void drawMovingState(ModularGuiContext context, float partialTicks) {}

    @Override
    public boolean onDragStart(int mouseButton) {
        if (mouseButton == 0 && canvas.isMouseInsideCanvas()) {
            dragStartX = data.getX();
            dragStartY = data.getY();
            dragStartMouseX = getContext().getAbsMouseX();
            dragStartMouseY = getContext().getAbsMouseY();
            dragStartIntersect = canvas.getFlowchartWidgets()
                .values()
                .stream()
                .filter(widget -> widget.getArea().intersects(getArea()))
                .toList();
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
            return;
        }
        if (canvas.getGraph().isSnapToGrid()) {
            data.setX((int) (Math.round((double) data.getX() / CanvasWidget.GRID_SIZE) * CanvasWidget.GRID_SIZE));
            data.setY((int) (Math.round((double) data.getY() / CanvasWidget.GRID_SIZE) * CanvasWidget.GRID_SIZE));
            reposition();
        }
        adjustGroupMembership();
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
        return canvas.isMouseInsideCanvas() &&
            canvas.getFlowchartWidgets()
            .values()
            .stream()
            .filter(f -> !(f instanceof GroupWidget2) && !dragStartIntersect.contains(f))
            .map(AbstractWidget::getArea)
            .noneMatch(area -> area.intersects(getArea()));
    }

    public void removeFromGraph(){
        ((ParentWidget<?>) getParent()).remove(this);
        canvas.getFlowchartWidgets().remove(data.getId());
        dataContainer.remove(data.getId());
    }

    protected abstract Map<UUID, D> getDefaultContainer();

    protected void reposition() {
        pos(data.getX(), data.getY());
    }

    private void adjustGroupMembership(){
        ParentWidget<?> oldParent = (ParentWidget<?>) getParent();
        ParentWidget<?> newParent =  StreamSupport.stream(getContext().getAllBelowMouse().spliterator(), false)
            .filter(w -> (w instanceof FlowchartWidget<?,?> || w instanceof CanvasWidget) && w != this)
            .map(w -> (ParentWidget<?>) w)
            .findFirst() // this should always find at least 1 match (the canvas)
            .orElseThrow();

        if (oldParent != newParent && (newParent instanceof CanvasWidget || newParent instanceof GroupWidget2)){
            oldParent.remove(this);
            newParent.child(this);

            dataContainer.remove(data.getId());
            if (newParent instanceof GroupWidget2 groupWidget){
                dataContainer = groupWidget.getData().getChildren();
                data.setX(groupWidget.getMouseGroupX());
                data.setY(groupWidget.getMouseGroupY());
            }
            else {
                dataContainer = (Map<UUID, GraphData>) getDefaultContainer();
                data.setX(canvas.getMouseCanvasX());
                data.setY(canvas.getMouseCanvasY());
            }
            dataContainer.put(data.getId(), data);
            reposition();
        }
    }

    public static FlowchartWidget<?, ?> getFlowchartWidgetFromData(CanvasWidget canvas, GraphData data){
        return switch (data){
            case Note note -> new NoteWidget(canvas, note);
            case Group group -> new GroupWidget2(canvas, group);
            default -> throw new IllegalArgumentException("Unsupported data type: " + data.getClass());
        };
    }
}
