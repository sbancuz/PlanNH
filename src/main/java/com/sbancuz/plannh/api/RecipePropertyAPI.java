package com.sbancuz.plannh.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gtnewhorizon.gtnhlib.item.ItemStack2IntFunction;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
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

    private static final List<RecipeProperty<?>> properties = new ArrayList<>();
    private static final List<PropertyProvider> extractors = new ArrayList<>();
    private static final Map<String, RecipeResource<?>> resourceMap = new HashMap<>();

    // Built-in property constants (scalar metadata)
    public static final RecipeProperty<Integer> DURATION_TICKS = RecipeProperty
        .intProperty("durationTicks", "Duration", 0);
    public static final RecipeProperty<Long> TOTAL_EU = RecipeProperty.longProperty("totalEu", "Total EU", 0L);
    public static final RecipeProperty<Long> EU_PER_TICK = RecipeProperty.longProperty("euPerTick", "EU/t", 0L);

    // Built-in resource constants (flow resources used by Port)
    // spotless:off
    public static final RecipeResource<ItemStack> ITEM = RecipeResource
        .builder("item", "Item", new ItemStack(Blocks.dirt))
        .serialize((obj, stack) -> {
            obj.addProperty("id", Item.itemRegistry.getNameForObject(stack.getItem()));
            obj.addProperty("size", stack.stackSize);
            obj.addProperty("meta", stack.getItemDamage());
            if (stack.hasTagCompound()) {
                obj.addProperty("nbt", stack.getTagCompound().toString());
            }
        })
        .deserialize(obj -> {
            final String id = obj.get("id")
                .getAsString();
            final int size = obj.get("size")
                .getAsInt();
            final int meta = obj.get("meta")
                .getAsInt();
            final Object rawItem = Item.itemRegistry.getObject(id);
            if (!(rawItem instanceof final Item item)) return null;
            final ItemStack stack = new ItemStack(item, size, meta);
            if (obj.has("nbt")) {
                try {
                    stack.setTagCompound(
                        (NBTTagCompound) JsonToNBT.func_150315_a(
                            obj.get("nbt")
                                .getAsString()));
                } catch (final NBTException ignored) {}
            }
            return stack;
        })
        .displayFormatter(ItemStack::getDisplayName)
        .amountExtractor(stack -> stack.stackSize)
        .connectionChecker(ItemStack::isItemEqual)
        .hashCodeExtractor(
            s -> 31 * s.getItem()
                .hashCode() + s.getItemDamage())
        .build();
    // spotless:on

    public static final RecipeResource<FluidStack> FLUID = RecipeResource.<FluidStack>builder("fluid", "Fluid", null)
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
        registerProperty(TOTAL_EU);
        registerProperty(EU_PER_TICK);
        registerResource(ITEM);
        registerResource(FLUID);
    }

    public static void registerProperty(final RecipeProperty<?> property) {
        properties.add(property);
    }

    public static void registerExtractor(final PropertyProvider extractor) {
        extractors.add(extractor);
    }

    public static void registerResource(final RecipeResource<?> resource) {
        resourceMap.put(resource.getKey(), resource);
    }

    public static List<RecipeProperty<?>> getProperties() {
        return Collections.unmodifiableList(properties);
    }

    public static List<PropertyProvider> getExtractors() {
        return Collections.unmodifiableList(extractors);
    }

    public static RecipeResource<?> resourceForKey(final String key) {
        return resourceMap.get(key);
    }
}
