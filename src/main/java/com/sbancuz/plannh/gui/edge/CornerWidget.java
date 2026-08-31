package com.sbancuz.plannh.gui.edge;

import static com.sbancuz.plannh.gui.edge.ArrowWidget.MIN_EDGE_WIDTH;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;

public class CornerWidget extends ArrowComponentWidget {

    public CornerWidget(ArrowWidget parent, int x0, int y0, int x1, int y1, int x2, int y2, int outerColor,
        int innerColor) {
        super(parent);

        int xOffset;
        int yOffset;

        if (y0 == y1) { // incoming segment horizonal
            if (x0 < x2) xOffset = 0;
            else xOffset = 1;
            if (y0 < y2) yOffset = 1;
            else yOffset = 0;
        } else { // incoming segment vertical
            if (x0 < x2) xOffset = 1;
            else xOffset = 0;
            if (y0 < y2) yOffset = 0;
            else yOffset = 1;
        }

        background(
            new Rectangle().color(outerColor), // outer edge
            new Rectangle() { // central bit

                @Override
                public void draw(GuiContext context, int x0, int y0, int width, int height, WidgetTheme widgetTheme) {
                    super.draw(context, x0 + xOffset, y0 + yOffset, width - 1, height - 1, widgetTheme);
                }
            }.color(innerColor),
            new Rectangle() { // corner pixel

                @Override
                public void draw(GuiContext context, int x0, int y0, int width, int height, WidgetTheme widgetTheme) {
                    super.draw(
                        context,
                        x0 + (MIN_EDGE_WIDTH - 1) * xOffset,
                        y0 + (MIN_EDGE_WIDTH - 1) * yOffset,
                        1,
                        1,
                        widgetTheme);
                }
            }.color(outerColor));

        size(MIN_EDGE_WIDTH);
        pos(x1, y1);
    }
}
