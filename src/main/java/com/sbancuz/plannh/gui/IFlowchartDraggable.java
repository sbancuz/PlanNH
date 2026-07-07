package com.sbancuz.plannh.gui;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Area;

import it.unimi.dsi.fastutil.Pair;

public interface IFlowchartDraggable extends IDraggable {

    FlowchartWidget<?, ?> getFlowchartParent();

    Area getArea();

    IWidget getParent();

    @Override
    default void drawMovingState(ModularGuiContext modularGuiContext, float v) {
        getFlowchartParent().drawMovingState(modularGuiContext, v);
    }

    @Override
    default boolean onDragStart(int i) {
        Pair<Integer, Integer> offset = dragOffset();
        return getFlowchartParent().onDragStartWithOffset(i, offset.left(), offset.right());
    }

    @Override
    default void onDragEnd(boolean b) {
        getFlowchartParent().onDragEnd(b);
    }

    @Override
    default void onDrag(int i, long l) {
        getFlowchartParent().onDrag(i, l);
    }

    @Override
    default boolean canDropHere(int x, int y, @Nullable IWidget widget) {
        return getFlowchartParent().canDropHere(x, y, widget);
    }

    @Override
    default @Nullable Area getMovingArea() {
        return getFlowchartParent().getMovingArea();
    }

    @Override
    default boolean isMoving() {
        return getFlowchartParent().isMoving();
    }

    @Override
    default void setMoving(boolean b) {
        getFlowchartParent().setMoving(b);
    }

    default Pair<Integer, Integer> dragOffset() {
        Area area = getArea();
        int x = area.rx;
        int y = area.ry;
        IWidget parentWidget = getParent();
        while (parentWidget instanceof ParentWidget<?>parentWidgetActual && parentWidget != getFlowchartParent()) {
            Area parentArea = parentWidget.getArea();
            x += parentArea.rx;
            y += parentArea.ry;
            parentWidget = parentWidgetActual.getParent();
        }
        return Pair.of(x, y);
    }
}
