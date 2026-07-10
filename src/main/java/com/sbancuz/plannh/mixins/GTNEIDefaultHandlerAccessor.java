package com.sbancuz.plannh.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.gtnewhorizons.modularui.api.math.Pos2d;

import gregtech.api.recipe.NEIRecipeProperties;
import gregtech.nei.GTNEIDefaultHandler;

@Mixin(GTNEIDefaultHandler.class)
public interface GTNEIDefaultHandlerAccessor {

    @Accessor(remap = false)
    NEIRecipeProperties getNeiProperties();

    @Accessor(value = "WINDOW_OFFSET", remap = false)
    Pos2d getOffset();
}
