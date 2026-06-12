package com.sbancuz.plannh.mixins;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.sbancuz.plannh.nei.OpenFlowchartButton;

import codechicken.nei.Button;
import codechicken.nei.LayoutManager;
import codechicken.nei.VisiblityData;

@Mixin(value = LayoutManager.class, remap = false)
public class LayoutManagerMixin {

    private static final Button openFlowchartButton = new OpenFlowchartButton();

    private static final int FC_BUTTON_GAP = 2;
    private static final int FC_BUTTON_BOTTOM_OFF = 22;
    private static final int FC_BUTTON_SIZE = 20;

    @Inject(method = "updateWidgetVisiblities", at = @At("TAIL"), remap = false)
    private static void plannh$onUpdateWidgetVisiblities(final GuiContainer gui, final VisiblityData visiblity,
        final CallbackInfo ci) {
        if (!visiblity.showNEI) return;

        openFlowchartButton.x = LayoutManager.bookmarksButton.x + LayoutManager.bookmarksButton.w + FC_BUTTON_GAP;
        openFlowchartButton.y = gui.height - FC_BUTTON_BOTTOM_OFF;
        openFlowchartButton.h = FC_BUTTON_SIZE;
        openFlowchartButton.w = FC_BUTTON_SIZE;
        LayoutManager.addWidget(openFlowchartButton);
    }
}
