package com.sbancuz.plannh.data.flowchart;

import net.minecraftforge.fluids.FluidStack;

import lombok.Getter;

@Getter
public final class FluidPort extends Port {

    private final FluidStack stack;

    public FluidPort(final FluidStack stack, final float chance) {
        super(chance);
        this.stack = stack;
    }

    @Override
    public String getPortType() {
        return "fluid";
    }

    @Override
    public int getAmount() {
        return stack.amount;
    }

}
