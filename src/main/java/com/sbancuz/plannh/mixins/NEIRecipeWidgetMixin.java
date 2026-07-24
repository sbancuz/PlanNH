package com.sbancuz.plannh.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.NEIRecipeWidget;

@Mixin(value = NEIRecipeWidget.class, remap = false)
public class NEIRecipeWidgetMixin {

    // create separate lists for all widgets so they are separate from the recipe handler
    @Unique
    private List<PositionedStack> plannh$inputs;

    @Unique
    private List<PositionedStack> plannh$catalysts;

    @Inject(method = "getInputs", at = @At("TAIL"), cancellable = true)
    private void plannh$getInputs(CallbackInfoReturnable<List<PositionedStack>> cir) {
        if (plannh$inputs == null) plannh$inputs = cir.getReturnValue()
            .stream()
            .map(PositionedStack::copy)
            .toList();
        cir.setReturnValue(plannh$inputs);
    }

    @Inject(method = "getCatalysts", at = @At("TAIL"), cancellable = true)
    private void plannh$getCatalysts(CallbackInfoReturnable<List<PositionedStack>> cir) {
        if (plannh$catalysts == null) plannh$catalysts = cir.getReturnValue()
            .stream()
            .map(PositionedStack::copy)
            .toList();
        cir.setReturnValue(plannh$catalysts);
    }
}
