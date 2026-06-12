package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularScreen;

public class FlowchartGuiContainer extends GuiContainerWrapper {

    public FlowchartGuiContainer(final ModularContainer container, final ModularScreen screen) {
        super(container, screen);
    }

    @Override
    public void initGui() {
        this.ySize = this.height;
        applyNeiSizing(this.width);
        super.initGui();
    }

    public void applyNeiSizing(final int width) {
        this.xSize = width - 368;
        this.guiLeft = (width - this.xSize) / 2;
    }
}
