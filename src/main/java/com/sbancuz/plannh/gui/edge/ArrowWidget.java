package com.sbancuz.plannh.gui.edge;

import java.util.List;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.node.PortWidget;

// todo redo component backgrounds such that fractional edge width is supported

public class ArrowWidget extends ParentWidget<ArrowWidget> {

    public static final int MIN_EDGE_WIDTH = 4;

    private List<int[]> coords;

    private final CanvasWidget canvas;

    public ArrowWidget(CanvasWidget canvas) {
        this.canvas = canvas;
    }

    private void refresh() {
        int size = coords.size();
        if (size < 2) throw new IllegalStateException("invalid arrow");

        removeAll();

        // todo add filled & border color based on item color
        int outerColor = Color.WHITE.main;
        int innerColor = Color.BLACK.main;

        // initial edge
        child(
            new EdgeWidget(
                this,
                coords.get(0)[0],
                coords.get(0)[1],
                coords.get(1)[0],
                coords.get(1)[1],
                outerColor,
                innerColor));

        // corner + edge
        for (int i = 1; i < size - 1; i++) {
            child(
                new CornerWidget(
                    this,
                    coords.get(i - 1)[0],
                    coords.get(i - 1)[1],
                    coords.get(i)[0],
                    coords.get(i)[1],
                    coords.get(i + 1)[0],
                    coords.get(i + 1)[1],
                    outerColor,
                    innerColor));

            child(
                new EdgeWidget(
                    this,
                    coords.get(i)[0],
                    coords.get(i)[1],
                    coords.get(i + 1)[0],
                    coords.get(i + 1)[1],
                    outerColor,
                    innerColor));
        }

        // arrow head
        child(
            new HeadWidget(
                this,
                coords.get(size - 2)[0],
                coords.get(size - 2)[1],
                coords.getLast()[0],
                coords.getLast()[1],
                outerColor,
                innerColor));

        // helper pos, todo remove
        coords.forEach(
            pos -> child(
                new Rectangle().color(Color.GREEN.main)
                    .asWidget()
                    .size(1)
                    .pos(pos[0] + MIN_EDGE_WIDTH / 2, pos[1] + MIN_EDGE_WIDTH / 2)));
    }

    public void setCoords(List<int[]> coords) {
        this.coords = coords;
        refresh();
    }

    public void removeFromGraph() {
        canvas.getGraph()
            .getEdges2()
            .remove(edge.getId());
        canvas.getArrowWidgets()
            .remove(edge.getId());
        canvas.remove(this);
    }

    @Override
    public boolean canHover() {
        return PortWidget.arrowWidgetInCreation == null;
    }
}
