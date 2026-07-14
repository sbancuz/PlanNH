package com.sbancuz.plannh.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.sbancuz.plannh.gui.FlowchartScreen;

import gregtech.api.recipe.RecipeCategory;
import gregtech.nei.GTNEIDefaultHandler;

@Mixin(value = GTNEIDefaultHandler.class, remap = false)
public class GTNEIDefaultHandlerMixin {

    @Final
    @Shadow
    protected RecipeCategory recipeCategory;

    @Inject(method = "drawDescription", at = @At("HEAD"), cancellable = true)
    private void plannh$drawDescription(GTNEIDefaultHandler.CachedDefaultRecipe cachedRecipe, final CallbackInfo ci) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiContainerWrapper wrapper
            && wrapper.getScreen() instanceof FlowchartScreen) ci.cancel();
    }

    @Inject(method = "getRecipeName", at = @At("HEAD"), cancellable = true)
    private void plannh$getRecipeName(CallbackInfoReturnable<String> cir) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiContainerWrapper wrapper
            && wrapper.getScreen() instanceof FlowchartScreen)
            cir.setReturnValue(StatCollector.translateToLocal(recipeCategory.unlocalizedName));
    }
}
