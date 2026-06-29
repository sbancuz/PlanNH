package com.sbancuz.plannh.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipeResource;

public final class RecipePropertyAPI {

    private static final Map<String, List<PropertyProvider>> extractors = new HashMap<>();

    public static final RecipeProperty<Integer> DURATION_TICKS = RecipeProperty.<Integer>builder("duration_ticks", 0)
        .build();

    public static final RecipeResource<ItemStack> ITEM = RecipeResource.builder("item", new ItemStack(Blocks.dirt))
        .displayFormatter(ItemStack::getDisplayName)
        .amountFormatter((rate) -> {
            if (rate >= 1000000000f) return String.format("%.1fB", rate / 1000000000f);
            if (rate >= 1000000f) return String.format("%.1fM", rate / 1000000f);
            if (rate >= 1000f) return String.format("%.0f", rate);
            if (rate >= 1f) return String.format("%.2f", rate);
            return String.format("%.3f", rate);
        })
        .amountExtractor(stack -> stack.stackSize)
        .amountUpdater((stack, newAmount) -> stack.stackSize = newAmount)
        .connectionChecker(ItemStack::isItemEqual)
        .hashCodeExtractor(
            s -> 31 * s.getItem()
                .hashCode() + s.getItemDamage())
        .build();

    public static final RecipeResource<FluidStack> FLUID = RecipeResource
        .<FluidStack>builder("fluid", new FluidStack(FluidRegistry.WATER, 0, null))
        .displayFormatter(FluidStack::getLocalizedName)
        .amountFormatter(amount -> {
            final int mB = Math.round(amount);
            return mB >= 1000 ? String.format("%.1fB", mB / 1000f) : mB + "mB";
        })
        .amountExtractor(fs -> fs.amount)
        .amountUpdater((fs, newAmount) -> fs.amount = newAmount)
        .connectionChecker(FluidStack::isFluidEqual)
        .hashCodeExtractor(
            fs -> fs.getFluid()
                .hashCode())
        .build();

    public static void registerExtractor(String overlayId, final PropertyProvider extractor) {
        extractors.computeIfAbsent(overlayId, k -> new ArrayList<>())
            .add(extractor);
    }

    @Nonnull
    public static List<PropertyProvider> getExtractors(String overlayId) {
        return extractors.getOrDefault(overlayId, List.of());
    }

    public static @Nullable PropertyProvider getExtractor(String overlayId) {
        final List<PropertyProvider> list = extractors.get(overlayId);
        return list != null && !list.isEmpty() ? list.getFirst() : null;
    }

    public static void reset() {
        for (var ex : extractors.values()) {
            ex.clear();
        }
        extractors.clear();
    }
}
