package com.sbancuz.plannh.mixins;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.sbancuz.plannh.gui.FlowchartScreen;

import gregtech.nei.GTNEIDefaultHandler;

@Mixin(GTNEIDefaultHandler.class)
public class GTNEIDefaultHandlerMixin {

    @Inject(method = "drawDescription", at = @At("HEAD"), cancellable = true, remap = false)
    private void plannh$drawDescription(GTNEIDefaultHandler.CachedDefaultRecipe cachedRecipe, final CallbackInfo ci) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiContainerWrapper wrapper
            && wrapper.getScreen() instanceof FlowchartScreen) ci.cancel();
    }
}
