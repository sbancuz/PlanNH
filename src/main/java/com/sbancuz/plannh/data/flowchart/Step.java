package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.api.RecipePropertyAPI;

import gregtech.common.items.ItemFluidDisplay;
import lombok.Getter;

public class Step extends GraphData implements FlowData {

    private final transient List<Port<?>> inputs = new ArrayList<>();
    private final transient List<Port<?>> outputs = new ArrayList<>();

    @Getter
    // Here we can set a debug amount to make it work like a source/sink (Depends on the amount)
    private @Nullable ItemStack filter;

    public Step() {
        this(UUID.randomUUID());
    }

    public Step(final UUID id) {
        super(id);
    }

    public void setFilter(final ItemStack stack) {
        filter = stack.copy();
        filter.stackSize = 0;
        inputs.clear();
        outputs.clear();

        // TODO: add a toPort function to RecipeProperty, then figure out how to handle conflicts
        // Ports start at 0: filter.stackSize is the single source of truth for the amount,
        // so the transient ports always stay in sync with what gets serialized.
        if (Compat.GREGTECH.isLoaded && stack.getItem() instanceof ItemFluidDisplay) {
            final FluidStack fs = new FluidStack(FluidRegistry.getFluid(stack.getItemDamage()), 0);
            inputs.add(new Port<>(RecipePropertyAPI.FLUID, fs.copy(), 1.f));
            outputs.add(new Port<>(RecipePropertyAPI.FLUID, fs.copy(), 1.f));
        } else {
            inputs.add(new Port<>(RecipePropertyAPI.ITEM, filter.copy(), 1.f));
            outputs.add(new Port<>(RecipePropertyAPI.ITEM, filter.copy(), 1.f));
        }
    }

    /**
     * Rebuilds the transient input/output ports after GSON deserialization.
     * Must be called explicitly after {@code GSON.fromJson(..., Step.class)}.
     */
    public void init() {
        if (filter == null) return;
        final int amount = filter.stackSize;
        setFilter(filter);
        setAmount(amount);
    }

    public void setAmount(final int newAmount) {
        if (filter == null) return;
        filter.stackSize = newAmount;

        inputs.getFirst()
            .setAmount(0);
        outputs.getFirst()
            .setAmount(0);

        // Positive amount = provider/source: supply flows out of the output port.
        // Negative amount = sink/consumer: demand flows into the input port.
        if (newAmount > 0) {
            outputs.getFirst()
                .setAmount(newAmount);
        } else if (newAmount < 0) {
            inputs.getFirst()
                .setAmount(-newAmount);
        }
    }

    public int getAmount() {
        return filter == null ? 0 : filter.stackSize;
    }

    @Override
    public List<Port<?>> getInputs() {
        return inputs;
    }

    @Override
    public List<Port<?>> getOutputs() {
        return outputs;
    }

    @Override
    public String getType() {
        return "step";
    }
}
