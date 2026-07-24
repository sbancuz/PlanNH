package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.RecipeResource;

import codechicken.nei.PositionedStack;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Port<T> {

    private final RecipeResource<T> type;
    private final T value;
    private final List<Integer> indices; // corresponding indices for this resource in the recipe
    private float chance;

    public Port(final RecipeResource<T> type, final T value, final float chance, int index) {
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

    @SuppressWarnings("unchecked")
    public boolean canConnect(final Port<?> other) {
        if (!type.equals(other.type)) return false;
        return type.canConnect(value, (T) other.value);
    }

    public void merge(final Port<?> other) {
        final int newAmount = getAmount() + other.getAmount();
        this.chance = (this.getAmount() * this.chance + other.getAmount() * other.chance) / newAmount;
        this.indices.addAll(other.indices);
        type.setAmount(value, newAmount);
    }

    public static Port<ItemStack> itemPort(PositionedStack ps, int index) {
        return new Port<>(RecipePropertyAPI.ITEM, ps.item.copy(), (float) ps.getChance() / 10_000, index);
    }
}
