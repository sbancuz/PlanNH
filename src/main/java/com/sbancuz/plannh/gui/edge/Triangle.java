package com.sbancuz.plannh.gui.edge;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.BufferBuilder;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.Platform;

// an isosceles triangle
public class Triangle implements IDrawable {

    // A and B are the vertices of the base, C is the apex; all in ccw order
    private int colorA, colorB, colorC;
    private Direction direction;
    private boolean canApplyTheme = false;

    public Triangle(Direction direction) {
        color(0xFFFFFFFF);
        this.direction = direction;
    }

    public Triangle color(int color) {
        return color(color, color, color);
    }

    public Triangle color(int colorA, int colorB, int colorC) {
        this.colorA = colorA;
        this.colorB = colorB;
        this.colorC = colorC;
        return this;
    }

    public Triangle direction(Direction direction) {
        this.direction = direction;
        return this;
    }

    public Triangle canApplyTheme(boolean canApplyTheme) {
        this.canApplyTheme = canApplyTheme;
        return this;
    }

    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
        applyColor(widgetTheme.getColor());
        Platform.setupDrawColor();
        Platform.setupDrawGradient();
        Platform.startDrawing(Platform.DrawMode.TRIANGLES, Platform.VertexFormat.POS_COLOR, buffer -> {
            switch (direction) {
                case UP -> {
                    v(buffer, x, y + height, colorA);
                    v(buffer, x + width, y + height, colorB);
                    v(buffer, x + (float) width / 2, y, colorC);
                }
                case RIGHT -> {
                    v(buffer, x, y, colorA);
                    v(buffer, x, y + height, colorB);
                    v(buffer, x + width, y + (float) height / 2, colorC);
                }
                case DOWN -> {
                    v(buffer, x + width, y, colorA);
                    v(buffer, x, y, colorB);
                    v(buffer, x + (float) width / 2, y + height, colorC);
                }
                case LEFT -> {
                    v(buffer, x + width, y + height, colorA);
                    v(buffer, x + width, y, colorB);
                    v(buffer, x, y + (float) height / 2, colorC);
                }
            }
        });
        Platform.endDrawGradient();
        Platform.endDrawColor();
    }

    private static void v(BufferBuilder buffer, float x, float y, int c) {
        buffer.pos(x, y, 0)
            .color(Color.getRed(c), Color.getGreen(c), Color.getBlue(c), Color.getAlpha(c))
            .endVertex();
    }

    @Override
    public boolean canApplyTheme() {
        return this.canApplyTheme;
    }

    public enum Direction {
        UP,
        RIGHT,
        DOWN,
        LEFT;
    }
}
