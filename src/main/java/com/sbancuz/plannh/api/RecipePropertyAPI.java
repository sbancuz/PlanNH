package com.sbancuz.plannh.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.common.collect.ArrayListMultimap;
import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.properties.ResourceProperty;
import com.sbancuz.plannh.data.provider.DefaultProvider;
import com.sbancuz.plannh.gui.GuiHelper;
import com.sbancuz.plannh.gui.IngredientColors;
import com.sbancuz.plannh.gui.PlannhColors;
import com.sbancuz.plannh.mixins.PositionedStackAccessor;

import codechicken.nei.PositionedStack;
import gregtech.api.util.GTUtility;

public final class RecipePropertyAPI {

    private static final ArrayListMultimap<Class<?>, PropertyProvider> extractors = ArrayListMultimap.create();

    public static final RecipeProperty<Integer> DURATION_TICKS = RecipeProperty.<Integer>builder("duration_ticks", 0)
        .build();

    public static final ResourceProperty<ItemStack> ITEM = ResourceProperty.builder("item", new ItemStack(Blocks.dirt))
        .displayFormatter(ItemStack::getDisplayName)
        .amountFormatter(GuiHelper::formatRate)
        .amountExtractor(stack -> stack.stackSize)
        .amountUpdater((stack, newAmount) -> stack.stackSize = newAmount)
        .connectionChecker(RecipePropertyAPI::itemPortsMatch)
        .hashCodeExtractor(
            s -> 31 * s.getItem()
                .hashCode() + s.getItemDamage())
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
        .connectionChecker(RecipePropertyAPI::fluidPortsMatch)
        .hashCodeExtractor(
            fs -> fs.getFluid()
                .hashCode())
        .colorProvider(IngredientColors::fluidColor)
        .pinInputColor(PlannhColors.PIN_FLUID_IN.getColor())
        .pinOutputColor(PlannhColors.PIN_FLUID_OUT.getColor())
        .arrowColor(PlannhColors.ARROW_FLUID.getColor())
        .build();

    private static boolean itemPortsMatch(final Port<ItemStack> pa, final Port<ItemStack> pb) {
        PositionedStack psa = pa.getAllStacks()
            .getFirst();
        PositionedStack psb = pb.getAllStacks()
            .getFirst();

        ItemStack[] as = ((PositionedStackAccessor) psa).getPermutated() ? psa.items : new ItemStack[] { psa.item };
        ItemStack[] bs = ((PositionedStackAccessor) psb).getPermutated() ? psb.items : new ItemStack[] { psb.item };

        for (ItemStack a : as) {
            for (ItemStack b : bs) {
                if (a.isItemEqual(b)) return true;
            }
        }

        return false;
    }

    private static boolean fluidPortsMatch(final Port<FluidStack> pa, final Port<FluidStack> pb) {
        if (Compat.GREGTECH.isLoaded) {
            // gregtech has fluid alternatives so we need to check those
            FluidStack[] as = Arrays.stream(
                pa.getAllStacks()
                    .getFirst().items)
                .map(GTUtility::getFluidFromDisplayStack)
                .toArray(FluidStack[]::new);
            FluidStack[] bs = Arrays.stream(
                pb.getAllStacks()
                    .getFirst().items)
                .map(GTUtility::getFluidFromDisplayStack)
                .toArray(FluidStack[]::new);

            for (FluidStack a : as) {
                for (FluidStack b : bs) {
                    if (a.isFluidEqual(b)) return true;
                }
            }
        }

        return pa.getValue()
            .isFluidEqual(pb.getValue());
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
