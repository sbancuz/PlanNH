package com.sbancuz.plannh.nei;

import net.minecraft.client.gui.inventory.GuiContainer;

import com.sbancuz.plannh.gui.FlowchartGuiContainer;

import codechicken.nei.VisiblityData;
import codechicken.nei.api.INEIGuiAdapter;

public class FlowchartGuiHandler extends INEIGuiAdapter {

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        if (gui instanceof FlowchartGuiContainer) {
            currentVisibility.showSearchSection = true;
            currentVisibility.showWidgets = true;
            currentVisibility.showBookmarkPanel = false;
            currentVisibility.showItemSection = true;
        }
        return currentVisibility;
    }
}
