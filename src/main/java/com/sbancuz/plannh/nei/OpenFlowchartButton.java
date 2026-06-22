package com.sbancuz.plannh.nei;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.sbancuz.plannh.gui.FlowchartScreen;
import com.sbancuz.plannh.gui.PlanGuiContainer;

import codechicken.nei.Button;

public class OpenFlowchartButton extends Button {

    public OpenFlowchartButton() {
        super("FC");
    }

    @Nullable
    private GuiScreen previousScreen = null;

    @Override
    public boolean onButtonPress(final boolean rightclick) {
        if (!rightclick) {
            final Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen instanceof PlanGuiContainer) {
                mc.displayGuiScreen(previousScreen != null ? previousScreen : new GuiInventory(mc.thePlayer));
            } else {
                previousScreen = mc.currentScreen;
                final FlowchartScreen screen = FlowchartScreen.create();
                final ModularContainer container = new ModularContainer();
                container.constructClientOnly();
                final PlanGuiContainer wrapper = new PlanGuiContainer(container, screen);
                mc.displayGuiScreen(wrapper);
            }
            return true;
        }
        return false;
    }

    private static final int BUTTON_WIDTH = 20;

    @Override
    public int contentWidth() {
        return BUTTON_WIDTH;
    }
}
