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

    @Inject(method = "updateWidgetVisiblities", at = @At("TAIL"), remap = false)
    private static void plannh$onUpdateWidgetVisiblities(final GuiContainer gui, final VisiblityData visiblity,
        final CallbackInfo ci) {
        if (!visiblity.showNEI) return;

        openFlowchartButton.x = LayoutManager.bookmarksButton.x + LayoutManager.bookmarksButton.w + 2;
        openFlowchartButton.y = gui.height - 22;
        openFlowchartButton.h = 20;
        openFlowchartButton.w = 20;
        LayoutManager.addWidget(openFlowchartButton);
    }
}
