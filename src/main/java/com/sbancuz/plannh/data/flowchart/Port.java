package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.Color;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.properties.ResourceProperty;

import codechicken.nei.PositionedStack;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Port<T> {

    private final ResourceProperty<T> type;
    private final T value;
    private final List<Integer> indices; // corresponding indices for this resource in the recipe
    private float chance;

    public Port(final ResourceProperty<T> type, final T value, final float chance, int index) {
        this.type = type;
        this.value = value;
        this.chance = chance;
        this.indices = new ArrayList<>();
        indices.add(index);
    }

    public int getAmount() {
        return type.extractAmount(value);
    }

    public String getDisplayName() {
        return type.formatDisplayName(value);
    }

    /** The ItemStack recipe viewers (NEI) show for this port's ingredient; null if none. */
    @Nullable
    public ItemStack getDisplayStack() {
        return type.displayStack(value);
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
        return type.canConnect(value, (T) other.value);
    }

    public void merge(final Port<?> other) {
        final int newAmount = getAmount() + other.getAmount();
        // An amount-weighted average is undefined when there is no amount to weight by, and the NaN
        // chance it would leave here reaches the balancer as a NaN coefficient, where it poisons a
        // whole solve instead of failing anywhere near this line.
        if (newAmount != 0) chance = (getAmount() * chance + other.getAmount() * other.chance) / newAmount;
        indices.addAll(other.indices);
        type.setAmount(value, newAmount);
    }

    public static Port<ItemStack> itemPort(PositionedStack ps, int index) {
        return new Port<>(RecipePropertyAPI.ITEM, ps.item.copy(), (float) ps.getChance() / 10_000, index);
    }
}
