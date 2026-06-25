package com.sbancuz.plannh.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.sbancuz.plannh.gui.FlowchartScreen;
import com.sbancuz.plannh.mixins.GuiContainerAccessor;

import codechicken.nei.ItemsGrid;
import codechicken.nei.LayoutManager;
import codechicken.nei.LayoutStyleMinecraft;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.VisiblityData;

public class FlowchartLayoutStyle extends LayoutStyleMinecraft {

    private static final int PADDING = 2;
    private static final int SNAP_MARGIN = 2;
    private static final int NUMBER_OF_CHEAT_BUTTONS = 9;

    @Override
    public void layout(final GuiContainer gui, final VisiblityData visibility) {
        if (!(gui instanceof GuiContainerWrapper
            && ((GuiContainerWrapper) gui).getScreen() instanceof FlowchartScreen)) {
            super.layout(gui, visibility);
            return;
        }

        final GuiContainerAccessor accessor = (GuiContainerAccessor) gui;

        accessor.setYSize(accessor.getHeight());

        final int cols = Math.max(
            4,
            NEIClientConfig.getSetting("plannh.itemColumns")
                .getIntValue(9));

        final int narrowFreeSpace = ItemsGrid.SLOT_SIZE * cols + SNAP_MARGIN;
        final int narrowGap = 2 * (narrowFreeSpace + PADDING * 2);
        final int wideGap = 2 * (BUTTON_SIZE * NUMBER_OF_CHEAT_BUTTONS + PADDING * 2);

        accessor.setXSize(accessor.getWidth() - wideGap);
        super.layout(gui, visibility);

        accessor.setXSize(accessor.getWidth() - narrowGap);
        LayoutManager.itemPanel.resize(gui);
        LayoutManager.bookmarkPanel.resize(gui);
        LayoutManager.itemZoom.resize(gui);

        LayoutManager.itemPanel.pageLabel.x = LayoutManager.itemPanel.x
            + (LayoutManager.itemPanel.w - LayoutManager.itemPanel.pagePrev.w - PADDING) / 2;

        final ModularScreen screen = ((GuiContainerWrapper) gui).getScreen();
        screen.getMainPanel().scheduleResize();
    }
}
