package com.sbancuz.plannh.data.flowchart;

import javax.annotation.Nullable;

import lombok.Getter;
import net.minecraftforge.fluids.FluidStack;

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
