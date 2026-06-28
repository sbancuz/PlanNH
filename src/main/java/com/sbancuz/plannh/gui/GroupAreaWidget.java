package com.sbancuz.plannh.gui;

import static com.sbancuz.plannh.data.flowchart.Group.GROUP_MIN_H;
import static com.sbancuz.plannh.data.flowchart.Group.GROUP_MIN_W;

import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.api.widget.IDragResizeable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.ResizeDragArea;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.sbancuz.plannh.data.flowchart.Group;

public class GroupAreaWidget extends ParentWidget<GroupAreaWidget> implements IFlowchartDraggable, IDragResizeable {

    private static final int GROUP_PADDING = 10;

    private final GroupWidget parent;
    private final Group data;

    public GroupAreaWidget(GroupWidget parent) {
        this.parent = parent;
        data = parent.getData();
        configureCoverChildren();
        setEnabledIf(_ -> !data.isCollapsed());
    }

    @Override
    public FlowchartWidget<?, ?> getFlowchartParent() {
        return parent;
    }

    @Override
    public boolean isCurrentlyResizable() {
        return !data.isCoverChildren();
    }

    @Override
    public int getDragAreaSize() {
        return 10;
    }

    @Override
    public int getMinDragWidth() {
        return Math.max(
            GROUP_MIN_W + GROUP_PADDING,
            getChildren().stream()
                .filter(w -> w instanceof FlowchartWidget<?, ?>)
                .map(IWidget::getArea)
                .mapToInt(a -> a.rx + a.width)
                .max()
                .orElse(0));
    }

    @Override
    public int getMinDragHeight() {
        return Math.max(
            GROUP_MIN_H + GROUP_PADDING,
            getChildren().stream()
                .filter(w -> w instanceof FlowchartWidget<?, ?>)
                .map(IWidget::getArea)
                .mapToInt(a -> a.ry + a.height)
                .max()
                .orElse(0));
    }

    @Override
    public boolean keepPosOnDragResize() {
        return false;
    }

    @Override
    public void onDragResizeStart() {

    }

    @Override
    public Object getAdditionalHoverInfo(IViewportStack viewportStack, int mouseX, int mouseY) {
        Object superInfo = super.getAdditionalHoverInfo(viewportStack, mouseX, mouseY);
        if (superInfo instanceof ResizeDragArea resizeDragArea && resizeDragArea.top) return null;
        return superInfo;
    }

    @Override
    public void onDragResize() {
        // IDragResizeable.super.onDragResize();
        getParent().scheduleResize();
    }

    @Override
    public void onResized() {
        Area area = getArea();
        if (data.getWidth() != area.width || data.getHeight() != area.height) {
            // TODO add rx, ry?
            data.setWidth(area.width);
            data.setHeight(area.height);
        }
    }

    @Override
    public void onDragResizeEnd() {
        // parent.scheduleResize();
    }

    public void configureCoverChildren() {
        if (data.isCoverChildren()) {
            coverChildren(GROUP_MIN_W, GROUP_MIN_H);
            scheduleResize();
        } else {
            disableCoverChildren();
            size(Math.max(data.getWidth(), getMinDragWidth()), Math.max(data.getHeight(), getMinDragHeight()));
        }
    }
}
