package com.sbancuz.plannh.gui.edge;

import java.util.ArrayList;
import java.util.List;

import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.sbancuz.plannh.data.flowchart.Edge2;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.IngredientColors;
import com.sbancuz.plannh.gui.node.NodeWidget;
import com.sbancuz.plannh.gui.node.PortWidget;

import lombok.Getter;

// todo redo component backgrounds such that fractional edge width is supported

public class ArrowWidget extends ParentWidget<ArrowWidget> {

    public static final int MIN_EDGE_WIDTH = 4;

    private List<int[]> coords;

    @Getter
    private final CanvasWidget canvas;

    @Getter
    private final Edge2 edge;

    private final List<EdgeWidget> edges = new ArrayList<>();
    private final List<CornerWidget> corners = new ArrayList<>();
    private final HeadWidget head = new HeadWidget(this);
    private int size;

    private final int innerColor;
    private final int outerColor;

    // use this ctor in edge creation, creates an arrow without a backing edge data
    public ArrowWidget(CanvasWidget canvas) {
        this(canvas, null);
    }

    // normal ctor
    public ArrowWidget(CanvasWidget canvas, Edge2 edge) {
        this.canvas = canvas;
        this.edge = edge;
        child(head);

        if (edge != null) {
            canvas.getArrowWidgets()
                .put(edge.getId(), this);

            canvas.getNodeWidgets2()
                .get(edge.getTargetNodeId())
                .getArrowWidgets()
                .add(this);

            NodeWidget source = canvas.getNodeWidgets2()
                .get(edge.getSourceNodeId());
            source.getArrowWidgets()
                .add(this);

            innerColor = source.getPort(edge.getSourceOutputIndex(), false)
                .getArrowColor();
            outerColor = IngredientColors.outlineFor(innerColor);

            canvas.needsReroute();
        } else {
            innerColor = Color.BLACK.main;
            outerColor = Color.WHITE.main;
        }
    }

    private void refresh() {
        int newSize = coords.size();
        if (newSize < 2) throw new IllegalStateException("invalid arrow");

        // shift coords for proper positioning
        coords.forEach(coord -> {
            coord[0] -= MIN_EDGE_WIDTH / 2;
            coord[1] -= MIN_EDGE_WIDTH / 2;
        });

        if (newSize < size) {
            List<EdgeWidget> edgesForRemoval = edges.subList(newSize - 1, size - 1);
            edgesForRemoval.forEach(this::remove);
            edgesForRemoval.clear();

            List<CornerWidget> cornersForRemoval = corners.subList(newSize - 2, size - 2);
            cornersForRemoval.forEach(this::remove);
            cornersForRemoval.clear();
        } else if (newSize > size) {
            for (int i = 0; i < newSize - size; i++) {
                EdgeWidget edgeWidget = new EdgeWidget(this);
                edges.add(edgeWidget);
                child(edgeWidget);

                CornerWidget cornerWidget = new CornerWidget(this);
                corners.add(cornerWidget);
                child(cornerWidget);
            }
        }
        size = newSize;

        // initial edge
        edges.getFirst()
            .configure(outerColor, innerColor, coords.get(0), coords.get(1));

        // corner + edge
        for (int i = 1; i < size - 1; i++) {
            corners.get(i - 1)
                .configure(outerColor, innerColor, coords.get(i - 1), coords.get(i), coords.get(i + 1));

            edges.get(i)
                .configure(outerColor, innerColor, coords.get(i), coords.get(i + 1));
        }

        // arrow head
        head.configure(outerColor, innerColor, coords.get(size - 2), coords.getLast());
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
        canvas.needsReroute();
    }

    @Override
    public boolean canHover() {
        return PortWidget.arrowWidgetInCreation == null;
    }
}
