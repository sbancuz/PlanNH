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

    /**
     * Two-pass layout to reserve space for both the left cheat buttons and
     * the right item panel:
     * 1. Widen xSize so the left margin fits NUMBER_OF_CHEAT_BUTTONS
     * buttons, then run the default layout (buttons, search, …).
     * 2. Narrow xSize so the right margin fits `cols` item columns, then
     * re-layout only the item-side panels.
     *
     * guiLeft/guiTop must follow xSize/ySize because GuiContainer computes
     * them only in initGui() and NEI reads them for visibility checks.
     *
     * pageLabel.text is refreshed after resize() because resizeHeader()
     * runs before grid.refresh(), so the label would show the previous
     * frame's page count.
     */
    @Override
    public void layout(final GuiContainer gui, final VisiblityData visibility) {
        if (!(gui instanceof GuiContainerWrapper
            && ((GuiContainerWrapper) gui).getScreen() instanceof FlowchartScreen)) {
            super.layout(gui, visibility);
            return;
        }

        final GuiContainerAccessor accessor = (GuiContainerAccessor) gui;
        final int screenW = accessor.getWidth();
        final int screenH = accessor.getHeight();

        accessor.setYSize(screenH);
        accessor.setGuiTop(0);

        final int cols = Math.max(
            NEIPlanConfig.ConfigItemColumns.min,
            NEIClientConfig.getSetting(NEIPlanConfig.ConfigItemColumns.KEY)
                .getIntValue(NEIPlanConfig.ConfigItemColumns.defVal));

        // Pass 1: wide left margin for cheat buttons.
        final int wideGap = 2 * (BUTTON_SIZE * NUMBER_OF_CHEAT_BUTTONS + PADDING * 2);
        accessor.setXSize(screenW - wideGap);
        accessor.setGuiLeft(wideGap / 2);
        super.layout(gui, visibility);

        // Pass 2: narrow right margin for `cols` item columns.
        final int narrowFreeSpace = ItemsGrid.SLOT_SIZE * cols + SNAP_MARGIN;
        final int narrowGap = 2 * (narrowFreeSpace + PADDING * 2);
        accessor.setXSize(screenW - narrowGap);
        accessor.setGuiLeft(narrowGap / 2);

        LayoutManager.itemPanel.resize(gui);

        // Refresh label after resize() finally updates perPage.
        LayoutManager.itemPanel.pageLabel.text = LayoutManager.itemPanel.getLabelText();

        LayoutManager.bookmarkPanel.resize(gui);
        LayoutManager.itemZoom.resize(gui);

        final ModularScreen screen = ((GuiContainerWrapper) gui).getScreen();
        screen.getMainPanel()
            .scheduleResize();
    }
}
