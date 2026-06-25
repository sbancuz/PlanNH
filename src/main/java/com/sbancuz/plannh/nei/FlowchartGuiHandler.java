package com.sbancuz.plannh.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.sbancuz.plannh.gui.FlowchartScreen;

import codechicken.nei.VisiblityData;
import codechicken.nei.api.INEIGuiAdapter;

public class FlowchartGuiHandler extends INEIGuiAdapter {

    @Override
    public VisiblityData modifyVisiblity(final GuiContainer gui, final VisiblityData currentVisibility) {
        if (gui instanceof GuiContainerWrapper && ((GuiContainerWrapper) gui).getScreen() instanceof FlowchartScreen) {
            currentVisibility.showSearchSection = true;
            currentVisibility.showWidgets = true;
            currentVisibility.showBookmarkPanel = false;
            currentVisibility.showItemSection = true;
        }
        return currentVisibility;
    }
}
