package com.sbancuz.plannh.mixins;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.sbancuz.plannh.nei.OpenFlowchartButton;

import codechicken.nei.Button;
import codechicken.nei.LayoutManager;
import codechicken.nei.VisiblityData;

@Mixin(value = LayoutManager.class, remap = false)
public class LayoutManagerMixin {

    @Unique
    private static final Button planNH$openFlowchartButton = new OpenFlowchartButton();

    @Unique
    private static final int FC_BUTTON_GAP = 2;
    @Unique
    private static final int FC_BUTTON_BOTTOM_OFF = 22;
    @Unique
    private static final int FC_BUTTON_SIZE = 20;

    @Inject(method = "updateWidgetVisiblities", at = @At("TAIL"))
    private static void plannh$onUpdateWidgetVisiblities(final GuiContainer gui, final VisiblityData visiblity,
        final CallbackInfo ci) {
        if (!visiblity.showNEI) return;

        planNH$openFlowchartButton.x = LayoutManager.bookmarksButton.x + LayoutManager.bookmarksButton.w
            + FC_BUTTON_GAP;
        planNH$openFlowchartButton.y = gui.height - FC_BUTTON_BOTTOM_OFF;
        planNH$openFlowchartButton.h = FC_BUTTON_SIZE;
        planNH$openFlowchartButton.w = FC_BUTTON_SIZE;
        LayoutManager.addWidget(planNH$openFlowchartButton);
    }
}
