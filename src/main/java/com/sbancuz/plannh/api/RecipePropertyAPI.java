package com.sbancuz.plannh.api;

import java.util.HashMap;
import java.util.Map;

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
    private static final Map<String, PropertyProvider> extractors = new HashMap<>();

    // Built-in property constants (scalar metadata)
    public static final RecipeProperty<Integer> DURATION_TICKS = RecipeProperty.intProperty("durationTicks", 0);

    // Built-in resource constants (flow resources used by Port)
    public static final RecipeResource<ItemStack> ITEM = RecipeResource.builder("item", new ItemStack(Blocks.dirt))
        .serialize((obj, stack) -> {
            if (stack == null) return;
            NBTTagCompound nbt = new NBTTagCompound();
            stack.writeToNBT(nbt);
            obj.addProperty("itemStack", nbt.toString());
        })
        .deserialize(obj -> {
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
        .amountExtractor(stack -> stack.stackSize)
        .connectionChecker(ItemStack::isItemEqual)
        .hashCodeExtractor(
            s -> 31 * s.getItem()
                .hashCode() + s.getItemDamage())
        .build();

    public static final RecipeResource<FluidStack> FLUID = RecipeResource.<FluidStack>builder("fluid", null)
        .serialize((obj, stack) -> {
            obj.addProperty("fluid", FluidRegistry.getFluidName(stack.getFluid()));
            obj.addProperty("amount", stack.amount);
        })
        .deserialize(obj -> {
            final String name = obj.get("fluid")
                .getAsString();
            final int amount = obj.get("amount")
                .getAsInt();
            return FluidRegistry.getFluidStack(name, amount);
        })
        .displayFormatter(FluidStack::getLocalizedName)
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
        extractors.put(overlayId, extractor);
    }

    public static @Nullable RecipeProperty<?> getProperty(String key) {
        return properties.get(key);
    }

    public static @Nullable PropertyProvider getExtractor(String overlayId) {
        return extractors.get(overlayId);
    }
}
