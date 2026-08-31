package com.sbancuz.plannh.gui.edge;

import static com.sbancuz.plannh.gui.edge.ArrowWidget.MIN_EDGE_WIDTH;

import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;

public class HeadWidget extends ArrowComponentWidget {

    public HeadWidget(ArrowWidget parent) {
        super(parent);
    }

    @Override
    public void configure(int outerColor, int innerColor, int[]... coords) {
        int x0 = coords[0][0];
        int y0 = coords[0][1];
        int x1 = coords[1][0];
        int y1 = coords[1][1];

        Triangle.Direction direction;

        if (y0 == y1) { // incoming segment horizontal
            if (x0 < x1) direction = Triangle.Direction.RIGHT;
            else direction = Triangle.Direction.LEFT;
        } else { // incoming segment vertical
            if (y0 < y1) direction = Triangle.Direction.DOWN;
            else direction = Triangle.Direction.UP;
        }

        background(new Triangle(direction).color(outerColor), new Triangle(direction) {

            @Override
            public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
                switch (direction) {
                    case UP -> super.draw(context, x + 1, y + 1, width - 2, height - 1, widgetTheme);
                    case RIGHT -> super.draw(context, x, y + 1, width - 1, height - 2, widgetTheme);
                    case DOWN -> super.draw(context, x + 1, y, width - 2, height - 1, widgetTheme);
                    case LEFT -> super.draw(context, x + 1, y + 1, width - 1, height - 2, widgetTheme);
                }
            }
        }.color(innerColor));

        size(MIN_EDGE_WIDTH);

        pos(x1, y1);
    }
}
