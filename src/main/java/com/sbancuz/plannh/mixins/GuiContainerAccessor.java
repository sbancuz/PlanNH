package com.sbancuz.plannh.mixins;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiContainer.class)
public interface GuiContainerAccessor extends GuiScreenAccessor {

    @Accessor("xSize")
    void setXSize(int xSize);

    @Accessor("ySize")
    void setYSize(int ySize);

    @Accessor("guiLeft")
    void setGuiLeft(int guiLeft);

    @Accessor("guiTop")
    void setGuiTop(int guiTop);
}
