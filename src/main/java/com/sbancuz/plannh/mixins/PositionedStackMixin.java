package com.sbancuz.plannh.mixins;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codechicken.nei.PositionedStack;

@Mixin(value = PositionedStack.class, remap = false)
public class PositionedStackMixin {

    @Shadow
    protected boolean permutated;

    @Shadow
    public ItemStack[] items;

    @Inject(method = "setPermutationToRender(I)V", at = @At("HEAD"), cancellable = true)
    public void plannh$setPermutationToRender(int index, CallbackInfo ci) {
        if (items.length > 1 && !permutated) ci.cancel(); // add check for flowchart screen on top?
    }
}
