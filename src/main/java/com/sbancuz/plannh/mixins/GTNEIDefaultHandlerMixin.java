package com.sbancuz.plannh.mixins;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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

    @Unique
    private int plannh$drawTicks;

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

    @Inject(method = "onUpdate", at = @At("TAIL"))
    public void plannh$onUpdate(CallbackInfo ci) {
        // needed because base implementation is static -> more nodes = faster progress bar
        plannh$drawTicks++;
    }

    @ModifyArg(
        method = "<init>",
        index = 5,
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/nei/GTNEIDefaultHandler$NEITemplateContext;<init>(Lcom/gtnewhorizons/modularui/api/forge/IItemHandlerModifiable;Lcom/gtnewhorizons/modularui/api/forge/IItemHandlerModifiable;Lcom/gtnewhorizons/modularui/api/forge/IItemHandlerModifiable;Lcom/gtnewhorizons/modularui/api/forge/IItemHandlerModifiable;Lcom/gtnewhorizons/modularui/api/forge/IItemHandlerModifiable;Ljava/util/function/Supplier;Ljava/util/function/Supplier;Lcom/gtnewhorizons/modularui/api/math/Pos2d;)V"))
    private Supplier<Float> modifyProgressSupplier(Supplier<Float> progressSupplier) {
        return () -> (float) plannh$drawTicks % 200 / 200;
    }
}
