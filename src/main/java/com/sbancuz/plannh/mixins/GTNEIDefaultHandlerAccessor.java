package com.sbancuz.plannh.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import gregtech.api.recipe.NEIRecipeProperties;
import gregtech.nei.GTNEIDefaultHandler;

@Mixin(value = GTNEIDefaultHandler.class, remap = false)
public interface GTNEIDefaultHandlerAccessor {

    @Accessor()
    NEIRecipeProperties getNeiProperties();
}
