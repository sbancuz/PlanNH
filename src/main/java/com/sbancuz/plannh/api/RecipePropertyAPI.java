package com.sbancuz.plannh.api;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.google.common.collect.ArrayListMultimap;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.properties.ResourceProperty;
import com.sbancuz.plannh.data.provider.DefaultProvider;

public final class RecipePropertyAPI {

    private static final ArrayListMultimap<Class<?>, PropertyProvider> extractors = ArrayListMultimap.create();

    public static final RecipeProperty<Integer> DURATION_TICKS = RecipeProperty.<Integer>builder("duration_ticks", 0)
        .build();

    public static final ResourceProperty<ItemStack> ITEM = ResourceProperty.builder("item", new ItemStack(Blocks.dirt))
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
        .connectionChecker(RecipePropertyAPI::itemsMatch)
        .hashCodeExtractor(
            s -> 31 * s.getItem()
                .hashCode() + s.getItemDamage())
        .build();

    public static final ResourceProperty<FluidStack> FLUID = ResourceProperty
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

    private static boolean itemsMatch(final ItemStack a, final ItemStack b) {
        if (a.getItem() == null || b.getItem() == null) return false;
        if (a.isItemEqual(b)) return true;
        final int[] idsA = OreDictionary.getOreIDs(a);
        final int[] idsB = OreDictionary.getOreIDs(b);
        if (idsA.length == 0 || idsB.length == 0) return false;
        for (final int idA : idsA) {
            for (final int idB : idsB) {
                if (idA == idB) return true;
            }
        }
        return false;
    }

    public static void registerExtractor(final Class<?> handlerClass, final PropertyProvider extractor) {
        extractors.put(handlerClass, extractor);
    }

    @Nonnull
    public static List<PropertyProvider> getExtractors(final Class<?> handlerClass) {
        final List<PropertyProvider> result = new ArrayList<>();
        Class<?> clazz = handlerClass;
        while (clazz != null) {
            result.addAll(extractors.get(clazz));
            clazz = clazz.getSuperclass();
        }
        return result;
    }

    @Nonnull
    public static PropertyProvider getExtractor(final Class<?> handlerClass) {
        final List<PropertyProvider> list = getExtractors(handlerClass);
        return list.isEmpty() ? DefaultProvider.INSTANCE : list.getFirst();
    }

    public static void reset() {
        extractors.clear();
    }
}
