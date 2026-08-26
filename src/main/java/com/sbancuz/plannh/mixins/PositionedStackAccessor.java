package com.sbancuz.plannh.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import codechicken.nei.PositionedStack;

@Mixin(value = PositionedStack.class, remap = false)
public interface PositionedStackAccessor {

    @Accessor
    boolean getPermutated();

    @Accessor
    void setPermutated(boolean permutated);
}
