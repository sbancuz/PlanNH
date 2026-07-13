package com.sbancuz.plannh.data.flowchart;

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

    @SuppressWarnings("unchecked")
    public boolean canConnect(final Port<?> other) {
        if (!type.equals(other.type)) return false;
        return type.canConnect(value, (T) other.value);
    }

    public void merge(final Port<?> other) {
        final int newAmount = getAmount() + other.getAmount();
        this.chance = (this.getAmount() * this.chance + other.getAmount() * other.chance) / newAmount;
        type.setAmount(value, newAmount);
    }
}
