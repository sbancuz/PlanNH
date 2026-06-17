package com.sbancuz.plannh.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipeResource;

public final class RecipePropertyAPI {

    private static final Map<String, RecipeProperty<?>> properties = new HashMap<>();
    private static final Map<String, List<PropertyProvider>> extractors = new HashMap<>();

    public static final RecipeProperty<Integer> DURATION_TICKS = RecipeProperty.intBuilder("duration_ticks", 0)
        .build();

    public static final RecipeResource<ItemStack> ITEM = RecipeResource.builder("item", new ItemStack(Blocks.dirt))
        .serializer((obj, stack) -> {
            if (stack == null) return;
            NBTTagCompound nbt = new NBTTagCompound();
            stack.writeToNBT(nbt);
            obj.addProperty("itemStack", nbt.toString());
        })
        .deserializer(obj -> {
            if (!obj.has("itemStack")) return null;
            try {
                NBTTagCompound nbt = (NBTTagCompound) JsonToNBT.func_150315_a(
                    obj.get("itemStack")
                        .getAsString());
                return ItemStack.loadItemStackFromNBT(nbt);
            } catch (final NBTException e) {
                return null;
            }
        })
        .displayFormatter(ItemStack::getDisplayName)
        .amountFormatter((rate) -> {
            if (rate >= 1000000000f) return String.format("%.1fB", rate / 1000000000f);
            if (rate >= 1000000f) return String.format("%.1fM", rate / 1000000f);
            if (rate >= 1000f) return String.format("%.0f", rate);
            if (rate >= 1f) return String.format("%.2f", rate);
            return String.format("%.3f", rate);
        })
        .amountExtractor(stack -> stack.stackSize)
        .connectionChecker(ItemStack::isItemEqual)
        .hashCodeExtractor(
            s -> 31 * s.getItem()
                .hashCode() + s.getItemDamage())
        .build();

    public static final RecipeResource<FluidStack> FLUID = RecipeResource
        .<FluidStack>builder("fluid", new FluidStack(FluidRegistry.WATER, 0, null))
        .serializer((obj, stack) -> {
            obj.addProperty("fluid", FluidRegistry.getFluidName(stack.getFluid()));
            obj.addProperty("amount", stack.amount);
        })
        .deserializer(obj -> {
            final String name = obj.get("fluid")
                .getAsString();
            final int amount = obj.get("amount")
                .getAsInt();
            return FluidRegistry.getFluidStack(name, amount);
        })
        .displayFormatter(FluidStack::getLocalizedName)
        .amountFormatter(amount -> {
            final int mB = Math.round(amount);
            return mB >= 1000 ? String.format("%.1fB", mB / 1000f) : mB + "mB";
        })
        .amountExtractor(fs -> fs.amount)
        .connectionChecker(FluidStack::isFluidEqual)
        .hashCodeExtractor(
            fs -> fs.getFluid()
                .hashCode())
        .build();

    static {
        registerProperty(DURATION_TICKS);
        registerProperty(ITEM);
        registerProperty(FLUID);
    }

    public static void registerProperty(final RecipeProperty<?> property) {
        properties.put(property.getKey(), property);
    }

    public static void registerExtractor(String overlayId, final PropertyProvider extractor) {
        extractors.computeIfAbsent(overlayId, k -> new ArrayList<>())
            .add(extractor);
    }

    @Nonnull
    public static List<PropertyProvider> getExtractors(String overlayId) {
        return extractors.getOrDefault(overlayId, List.of());
    }

    public static @Nullable RecipeProperty<?> getProperty(String key) {
        return properties.get(key);
    }

    public static @Nullable PropertyProvider getExtractor(String overlayId) {
        final List<PropertyProvider> list = extractors.get(overlayId);
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }
}
