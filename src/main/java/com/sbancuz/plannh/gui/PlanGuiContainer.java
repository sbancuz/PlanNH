package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularScreen;

import codechicken.nei.ItemsGrid;
import codechicken.nei.NEIClientConfig;

public class PlanGuiContainer extends GuiContainerWrapper {

    public PlanGuiContainer(final ModularContainer container, final ModularScreen screen) {
        super(container, screen);
    }

    @Override
    public void initGui() {
        this.ySize = this.height;
        applyNeiSizing(this.width);
        super.initGui();
    }

    public void applyNeiSizing(final int width) {
        final int cols = Math.max(
            1,
            NEIClientConfig.getSetting("plannh.itemColumns")
                .getIntValue(9));
        final int panelWidth = cols * ItemsGrid.SLOT_SIZE * 2;
        this.xSize = width - panelWidth;
        this.guiLeft = (width - this.xSize) / 2;
    }
}
