package com.sbancuz.plannh.mixins;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiScreen.class)
public interface GuiScreenAccessor {

    @Accessor("width")
    int getWidth();

    @Accessor("height")
    int getHeight();
}
