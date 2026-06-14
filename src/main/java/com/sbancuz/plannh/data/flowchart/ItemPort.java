package com.sbancuz.plannh.data.flowchart;

import javax.annotation.Nullable;

import lombok.Getter;
import net.minecraft.item.ItemStack;

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
