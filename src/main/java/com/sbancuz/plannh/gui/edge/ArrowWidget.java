package com.sbancuz.plannh.gui.edge;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.sbancuz.plannh.gui.CanvasWidget;

import lombok.Getter;
import lombok.Setter;

public class ArrowWidget extends ParentWidget<ArrowWidget> {

    public static final int MIN_EDGE_WIDTH = 4;

    private List<int[]> coords;
    private final List<int[]> fixCoords = new ArrayList<>();

    private final CanvasWidget canvas;

    @Setter
    @Getter
    private boolean follow = false;

    public ArrowWidget(CanvasWidget canvas) {
        this.canvas = canvas;

//        coverChildren();

        fixCoords.add(new int[] { 123, 543 });

        // coords = List.of(
        // new int[] { -2, -2 },
        // new int[] { 334, -2 },
        // new int[] { 334, 198 },
        // new int[] { 32, 198 },
        // new int[] { 32, 34 },
        // new int[] { 376, 34 },
        // new int[] { 376, 208 },
        // new int[] { 618, 208 },
        // new int[] { 618, 42 },
        // new int[] { 396, 42 },
        // new int[] { 396, 276 },
        // new int[] { 478, 276 },
        // new int[] { 478, 300 });

        // coords = List.of(new int[] { 200, 100 }, new int[] { 100, 100 });

//        refresh();
    }

    private void refresh() {
        int size = coords.size();
        if (size < 2) throw new IllegalStateException("invalid arrow");

        removeAll();
        normalize();

        // todo add filled & border color based on item color
        int outerColor = Color.WHITE.main;
        int innerColor = Color.BLACK.main;

        // initial edge
        child(
            new EdgeWidget(
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

    private void normalize() {

        // move every edge such that the leftmost and topmost ones are at 0 x or y
        var minPos = coords.stream().reduce((pos1, pos2) -> new int[]{Math.min(pos1[0], pos2[0]), Math.min(pos1[1], pos2[1])}).orElseThrow();

//        coords.forEach(coord -> {
//            coord[0] -= minPos[0] + MIN_EDGE_WIDTH / 2;
//            coord[1] -= minPos[1] + MIN_EDGE_WIDTH / 2;
//        });

        // shorten last section in specific cases
        // if (coords.getLast()[0] > coords.get(coords.size() - 2)[0]) coords.getLast()[0] -= MIN_EDGE_WIDTH;
    }

    public void setCoords(List<int[]> coords) {
        this.coords = coords;
        refresh();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (follow) {
            int mouseX = canvas.getCanvasMouseX();
            int mouseY = canvas.getCanvasMouseY();

            List<int[]> temp = new ArrayList<>(fixCoords);

            temp.add(
                fixCoords.size() == 1 || fixCoords.getLast()[0] == fixCoords.get(fixCoords.size() - 2)[0]
                    ? new int[] { mouseX, fixCoords.getLast()[1] }
                    : new int[] { fixCoords.getLast()[0], mouseY });
            temp.add(new int[] { mouseX, mouseY });

            setCoords(temp);
        }
    }

    public void increment() {
        if (follow) {
            fixCoords.add(coords.get(coords.size() - 2));
            fixCoords.add(coords.getLast());
        }
    }
}
