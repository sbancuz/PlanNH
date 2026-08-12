package com.sbancuz.plannh.gui.edge;

import java.util.List;

import com.cleanroommc.modularui.widget.ParentWidget;

public class ArrowWidget extends ParentWidget<ArrowWidget> {

    private static final int MIN_WIDTH = 3;

    private List<int[]> coords;

    public ArrowWidget() {
        coords = List.of(new int[] { 0, 0 }, new int[] { 100, 0 }, new int[] { 100, 200 });

        coverChildren();

        refresh();
    }

    private void refresh() {
        if (coords.size() < 2) throw new IllegalStateException("invalid arrow");

        removeAll();
        normalize();
        for (int i = 0; i < coords.size() - 1; i++) {
            EdgeWidget edgeWidget = new EdgeWidget(i);
            edgeWidget.pos(coords.get(i)[0], coords.get(i)[1]);
            edgeWidget.size(
                Math.max(coords.get(i + 1)[0] - coords.get(i)[0], 3),
                Math.max(coords.get(i + 1)[1] - coords.get(i)[1], 3));
            child(edgeWidget);
        }
    }

    private void normalize() {
        int x0 = coords.getFirst()[0];
        int y0 = coords.getFirst()[1];

        coords.forEach(coord -> {
            coord[0] -= x0;
            coord[1] -= y0;
        });
    }

    public void setCoords(List<int[]> coords) {
        this.coords = coords;
        refresh();
    }
}
