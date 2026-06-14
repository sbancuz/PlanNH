package com.sbancuz.plannh.data.flowchart;

import net.minecraft.item.ItemStack;

import lombok.Getter;

@Getter
public final class ItemPort extends Port {

    private final ItemStack stack;

    public ItemPort(final ItemStack stack, final float chance) {
        super(chance);
        this.stack = stack;
    }

    @Override
    public String getPortType() {
        return "item";
    }

    @Override
    public int getAmount() {
        return stack.stackSize;
    }

}
