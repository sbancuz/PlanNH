package com.sbancuz.plannh.api;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.google.common.collect.ArrayListMultimap;
import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.properties.ResourceProperty;
import com.sbancuz.plannh.data.provider.DefaultProvider;
import com.sbancuz.plannh.data.provider.gregtech.GTHooks;
import com.sbancuz.plannh.gui.GuiHelper;
import com.sbancuz.plannh.gui.IngredientColors;
import com.sbancuz.plannh.gui.PlannhColors;

public final class RecipePropertyAPI {

    private static final ArrayListMultimap<Class<?>, PropertyProvider> extractors = ArrayListMultimap.create();

    public static final RecipeProperty<Integer> DURATION_TICKS = RecipeProperty.<Integer>builder("duration_ticks", 0)
        .build();

    public static final ResourceProperty<ItemStack> ITEM = ResourceProperty.builder("item", new ItemStack(Blocks.dirt))
        .displayFormatter(ItemStack::getDisplayName)
        .amountFormatter(GuiHelper::formatRate)
        .amountExtractor(stack -> stack.stackSize)
        .amountUpdater((stack, newAmount) -> stack.stackSize = newAmount)
        .connectionChecker(RecipePropertyAPI::itemsMatch)
        .hashCodeExtractor(
            s -> 31 * s.getItem()
                .hashCode() + s.getItemDamage())
        .displayStackProvider(stack -> stack)
        .colorProvider(IngredientColors::itemColor)
        .pinInputColor(PlannhColors.PIN_INPUT.getColor())
        .pinOutputColor(PlannhColors.PIN_OUTPUT.getColor())
        .arrowColor(PlannhColors.ARROW_ITEM.getColor())
        .build();

    public static final ResourceProperty<FluidStack> FLUID = ResourceProperty
        .<FluidStack>builder("fluid", new FluidStack(FluidRegistry.WATER, 0, null))
        .displayFormatter(FluidStack::getLocalizedName)
        // Buckets past a thousand litres, litres below, and the same suffix table over whichever
        // unit is in play - so a big line reads 1.0kB rather than running out of digits at 1000.0B.
        .amountFormatter(amount -> {
            if (amount >= 1000f) return GuiHelper.trimTrailingZeros(GuiHelper.formatRate(amount / 1000f)) + "B";
            return GuiHelper.trimTrailingZeros(GuiHelper.formatRate(amount)) + "mB";
        })
        .amountExtractor(fs -> fs.amount)
        .amountUpdater((fs, newAmount) -> fs.amount = newAmount)
        .connectionChecker(FluidStack::isFluidEqual)
        .hashCodeExtractor(
            fs -> fs.getFluid()
                .hashCode())
        // Guard sits inside the lambda so GT classes only load if GT is present AND the
        // lambda actually runs (lambda bodies resolve their classes at call time, not here).
        .displayStackProvider(fs -> {
            if (Compat.GREGTECH.isLoaded) {
                final ItemStack display = GTHooks.fluidDisplayStack(fs);
                if (display != null) return display;
            }
            // Forge's registry fills only from a full container's worth, so normalize the
            // amount; null when the fluid has no registered container.
            return FluidContainerRegistry.fillFluidContainer(
                new FluidStack(fs.getFluid(), FluidContainerRegistry.BUCKET_VOLUME),
                FluidContainerRegistry.EMPTY_BUCKET.copy());
        })
        .colorProvider(IngredientColors::fluidColor)
        .pinInputColor(PlannhColors.PIN_FLUID_IN.getColor())
        .pinOutputColor(PlannhColors.PIN_FLUID_OUT.getColor())
        .arrowColor(PlannhColors.ARROW_FLUID.getColor())
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
