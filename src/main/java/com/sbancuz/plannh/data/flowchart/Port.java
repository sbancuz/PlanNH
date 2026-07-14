package com.sbancuz.plannh.data.flowchart;

import codechicken.nei.PositionedStack;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.RecipeResource;

import it.unimi.dsi.fastutil.Pair;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;


@Setter
@Getter
public class Port<T> {

    private final RecipeResource<T> type;
    private final T value;
    private final List<Pair<Integer, Integer>> positions;
    private float chance;

    public Port(final RecipeResource<T> type, final T value, final float chance, Pair<Integer, Integer> position) {
        this.type = type;
        this.value = value;
        this.chance = chance;
        this.positions = new ArrayList<>();
        positions.add(position);
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
        this.positions.addAll(other.positions);
        type.setAmount(value, newAmount);
    }

    public static Port<ItemStack> itemPort(PositionedStack ps){
        return new Port<>(RecipePropertyAPI.ITEM, ps.item.copy(), (float) ps.getChance() / 10_000, Pair.of(ps.relx, ps.rely));
    }
}
