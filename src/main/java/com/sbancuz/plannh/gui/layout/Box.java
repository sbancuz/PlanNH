package com.sbancuz.plannh.gui.layout;

public record Box(int x, int y, int width, int height) {

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean overlaps(final Box other) {
        return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
    }
}
