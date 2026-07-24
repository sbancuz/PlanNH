package com.sbancuz.plannh.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.NEIRecipeWidget;

@Mixin(value = NEIRecipeWidget.class, remap = false)
public interface NEIRecipeWidgetAccessor {

    @Accessor
    void setUpdate(boolean update);

    @Invoker
    List<PositionedStack> callGetInputs();

    @Invoker
    List<PositionedStack> callGetOutputs();

    @Invoker
    List<PositionedStack> callGetCatalysts();
}
