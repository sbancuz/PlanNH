package com.sbancuz.plannh.data.flowchart;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.Color;
import com.sbancuz.plannh.data.RecipeResource;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Port<T> {

    private final RecipeResource<T> type;
    private final T value;
    private float chance;

    public Port(final RecipeResource<T> type, final T value, final float chance) {
        this.type = type;
        this.value = value;
        this.chance = chance;
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

    /** Whether an NEI lookup stack refers to this port's ingredient. */
    public boolean matchesLookup(final ItemStack lookup) {
        return type.matchesLookup(value, lookup);
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
        return ((RecipeResource<Object>) type).canConnect(value, other.value);
    }

    public void merge(final Port<?> other) {
        final int newAmount = getAmount() + other.getAmount();
        this.chance = (this.getAmount() * this.chance + other.getAmount() * other.chance) / newAmount;
        type.setAmount(value, newAmount);
    }
}
