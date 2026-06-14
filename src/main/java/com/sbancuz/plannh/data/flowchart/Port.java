package com.sbancuz.plannh.data.flowchart;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public sealed abstract class Port permits ItemPort,FluidPort {

    private float chance;

    protected Port(final float chance) {
        this.chance = chance;
    }

    public abstract String getPortType();

    public abstract int getAmount();

    public abstract Object getStack();
}
