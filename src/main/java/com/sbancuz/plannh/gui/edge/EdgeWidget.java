package com.sbancuz.plannh.gui.edge;

import static com.sbancuz.plannh.gui.edge.ArrowWidget.MIN_EDGE_WIDTH;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;

public class EdgeWidget extends ArrowComponentWidget {

    public EdgeWidget(ArrowWidget parent, int x0, int y0, int x1, int y1, int outerColor, int innerColor) {
        super(parent);

        background(new Rectangle().color(outerColor), new Rectangle() {

            @Override
            public void draw(GuiContext context, int startX, int startY, int width, int height,
                WidgetTheme widgetTheme) {
                if (y0 == y1) super.draw(context, startX, startY + 1, width, height - 2, widgetTheme); // horizontal
                else super.draw(context, startX + 1, startY, width - 2, height, widgetTheme); // vertical
            }
        }.color(innerColor));

        size(
            Math.max(Math.abs(x1 - x0) - MIN_EDGE_WIDTH, MIN_EDGE_WIDTH),
            Math.max(Math.abs(y1 - y0) - MIN_EDGE_WIDTH, MIN_EDGE_WIDTH));

        pos(Math.min(x0, x1) + (y0 == y1 ? MIN_EDGE_WIDTH : 0), Math.min(y0, y1) + (x0 == x1 ? MIN_EDGE_WIDTH : 0));
    }
}
