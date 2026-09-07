package com.sbancuz.plannh.gui;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widget.AbstractWidget;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.sbancuz.plannh.data.flowchart.GraphData;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Note;

public abstract class GroupableWidget<T extends ParentWidget<T>, D extends GraphData> extends FlowchartWidget<T, D> {

    protected Map<UUID, GraphData> dataContainer;

    protected GroupableWidget(CanvasWidget canvas, D data) {
        super(canvas, data);
        this.dataContainer = (Map<UUID, GraphData>) getDefaultContainer();
    }

    @Override
    protected void handleDrag(boolean successful) {
        super.handleDrag(successful);
        if (successful) {
            adjustGroupMembership();
        }
    }

    @Override
    public boolean canDropHere(int x, int y, @Nullable IWidget widget) {
        return canvas.isMouseInsideCanvas() && canvas.getFlowchartWidgets()
            .values()
            .stream()
            .filter(f -> !(f instanceof GroupWidget) && !dragStartIntersect.contains(f))
            .map(AbstractWidget::getArea)
            .noneMatch(area -> area.intersects(getArea()));
    }

    public void removeFromGraph() {
        canvas.getFlowchartWidgets()
            .remove(data.getId());
        dataContainer.remove(data.getId());
    }

    private void adjustGroupMembership() {
        ParentWidget<?> oldParent = (ParentWidget<?>) getParent();
        ParentWidget<?> newParent = StreamSupport.stream(
            getContext().getAllBelowMouse()
                .spliterator(),
            false)
            .filter(w -> (w instanceof FlowchartWidget<?, ?> || w instanceof CanvasWidget) && w != this)
            .map(w -> (ParentWidget<?>) w)
            .findFirst() // this should always find at least 1 match (the canvas)
            .orElseThrow();

        if (oldParent != newParent && (newParent instanceof CanvasWidget || newParent instanceof GroupWidget)) {
            oldParent.remove(this);

            dataContainer.remove(data.getId());
            if (newParent instanceof GroupWidget groupWidget) {
                groupWidget.getAreaWidget()
                    .child(this);
                dataContainer = groupWidget.getData()
                    .getChildren();
                data.setX(groupWidget.getMouseGroupX() - dragOffsetX);
                data.setY(groupWidget.getMouseGroupY() - dragOffsetY);
            } else {
                newParent.child(this);
                dataContainer = (Map<UUID, GraphData>) getDefaultContainer();
                data.setX(canvas.getCanvasMouseX() - dragOffsetX);
                data.setY(canvas.getCanvasMouseY() - dragOffsetY);
            }

            dataContainer.put(data.getId(), data);
            reposition();
        }
    }

    protected abstract Map<UUID, D> getDefaultContainer();

    public static GroupableWidget<?, ?> getFlowchartWidgetFromData(CanvasWidget canvas, GraphData data) {
        Objects.requireNonNull(canvas);
        if (data instanceof Note note) return new NoteWidget(canvas, note);
        if (data instanceof Group group) return new GroupWidget(canvas, group);
        throw new IllegalArgumentException("Unsupported data type: " + data.getClass());
    }
}
