package com.sbancuz.plannh.data.flowchart;

import static codechicken.nei.PositionedStack.CHANCE_FULL;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.utils.Color;
import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.properties.ResourceProperty;
import com.sbancuz.plannh.mixins.PositionedStackAccessor;

import codechicken.nei.PositionedStack;
import gregtech.api.util.GTUtility;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Port<T> {

    private final ResourceProperty<T> type;
    private T value;
    private float chance;
    private final List<PositionedStack> allStacks = new ArrayList<>();

    public Port(final ResourceProperty<T> type, final T value, final float chance, PositionedStack stack) {
        this.type = type;
        this.value = value;
        this.chance = chance;
        allStacks.add(stack);
    }

    public int getAmount() {
        return type.extractAmount(value);
    }

    public String getDisplayName() {
        return type.formatDisplayName(value);
    }

    /** Pin color for this port's ingredient; the type's pin color when none is derivable. */
    public int getPinColor(final boolean input) {
        return colorOr(input ? type.getPinInputColor() : type.getPinOutputColor());
    }

    /** Edge-arrow color for this port's ingredient; the type's arrow color as fallback. */
    public int getArrowColor() {
        return colorOr(type.getArrowColor());
    }

    /** Representative color for this port's ingredient; {@code fallback}'s alpha is kept. */
    private int colorOr(final int fallback) {
        final int rgb = type.color(value);
        if (rgb == -1) return fallback;
        return Color.withAlpha(rgb, Color.getAlpha(fallback));
    }

    @SuppressWarnings("unchecked")
    public boolean canConnect(final Port<?> other) {
        if (!type.equals(other.type)) return false;
        return type.canConnect(this, (Port<T>) other);
    }

    public void merge(final Port<?> other) {
        final int newAmount = getAmount() + other.getAmount();
        // An amount-weighted average is undefined when there is no amount to weight by, and the NaN
        // chance it would leave here reaches the balancer as a NaN coefficient, where it poisons a
        // whole solve instead of failing anywhere near this line.
        if (newAmount != 0) chance = (getAmount() * chance + other.getAmount() * other.chance) / newAmount;
        allStacks.addAll(other.allStacks);
        type.setAmount(value, newAmount);
    }

    public static Port<ItemStack> itemPort(PositionedStack ps) {
        return new Port<>(RecipePropertyAPI.ITEM, ps.item.copy(), (float) ps.getChance() / CHANCE_FULL, ps.copy());
    }

    // pass a copy of the new value (item/fluid stack) to this method or else that gets modified
    public void setValue(T value) {
        type.setAmount(value, type.extractAmount(this.value));
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    public void setValue(int index) {
        ItemStack itemStack = allStacks.getFirst().items[index];
        if (type == RecipePropertyAPI.ITEM) ((Port<ItemStack>) this).setValue(itemStack.copy());

        if (type == RecipePropertyAPI.FLUID && Compat.GREGTECH.isLoaded) ((Port<FluidStack>) this).setValue(
            GTUtility.getFluidFromDisplayStack(itemStack)
                .copy());

        allStacks.forEach(ps -> {
            PositionedStackAccessor psa = (PositionedStackAccessor) ps;
            psa.setPermutated(true);
            ps.setPermutationToRender(itemStack);
            psa.setPermutated(false);
        });
    }
}
