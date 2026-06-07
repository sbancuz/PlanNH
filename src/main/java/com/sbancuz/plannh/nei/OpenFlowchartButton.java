package com.sbancuz.plannh.nei;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.screen.ModularContainer;
import com.sbancuz.plannh.gui.FlowchartGuiContainer;
import com.sbancuz.plannh.gui.FlowchartScreen;

import codechicken.nei.Button;

public class OpenFlowchartButton extends Button {

    public OpenFlowchartButton() {
        super("FC");
    }

    @Override
    public boolean onButtonPress(boolean rightclick) {
        if (!rightclick) {
            FlowchartScreen screen = FlowchartScreen.create();
            ModularContainer container = new ModularContainer();
            container.constructClientOnly();
            FlowchartGuiContainer wrapper = new FlowchartGuiContainer(container, screen);
            Minecraft.getMinecraft()
                .displayGuiScreen(wrapper);
            return true;
        }
        return false;
    }

    @Override
    public int contentWidth() {
        return 20;
    }
}
