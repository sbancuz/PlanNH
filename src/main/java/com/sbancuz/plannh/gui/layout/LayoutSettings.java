package com.sbancuz.plannh.gui.layout;

public record LayoutSettings(int nodeSpacing, int layerSpacing, int thoroughness, int groupMinWidth,
    int groupMinHeight) {

    public static final LayoutSettings DEFAULT = new LayoutSettings(20, 70, 30, 300, 200);
}
