package com.sbancuz.plannh.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.sbancuz.plannh.gui.FlowchartGuiContainer;
import com.sbancuz.plannh.gui.FlowchartScreen;

import codechicken.nei.Button;

public class OpenFlowchartButton extends Button {

    public OpenFlowchartButton() {
        super("FC");
    }

    private GuiScreen previousScreen = null;

    @Override
    public boolean onButtonPress(final boolean rightclick) {
        if (!rightclick) {
            final Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen instanceof FlowchartGuiContainer) {
                mc.displayGuiScreen(previousScreen != null ? previousScreen : new GuiInventory(mc.thePlayer));
            } else {
                previousScreen = mc.currentScreen;
                final FlowchartScreen screen = FlowchartScreen.create();
                final ModularContainer container = new ModularContainer();
                container.constructClientOnly();
                final FlowchartGuiContainer wrapper = new FlowchartGuiContainer(container, screen);
                mc.displayGuiScreen(wrapper);
            }
            return true;
        }
        return false;
    }

    @Override
    public int contentWidth() {
        return 20;
    }
}
