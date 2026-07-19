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
import net.minecraftforge.oredict.OreDictionary;

import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.data.PropertyProvider;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipeResource;
import com.sbancuz.plannh.data.provider.gregtech.GTHooks;
import com.sbancuz.plannh.gui.IngredientColors;
import com.sbancuz.plannh.gui.PlannhColors;

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
        .connectionChecker(RecipePropertyAPI::itemsMatch)
        .hashCodeExtractor(
            s -> 31 * s.getItem()
                .hashCode() + s.getItemDamage())
        .displayStackProvider(stack -> stack)
        .lookupMatcher(ItemStack::isItemEqual)
        .colorProvider(IngredientColors::itemColor)
        .pinInputColor(PlannhColors.PIN_INPUT.getColor())
        .pinOutputColor(PlannhColors.PIN_OUTPUT.getColor())
        .arrowColor(PlannhColors.ARROW_ITEM.getColor())
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
        // Guards sit inside the lambdas so GT classes only load if GT is present AND the
        // lambda actually runs (lambda bodies resolve their classes at call time, not here).
        .displayStackProvider(fs -> Compat.GREGTECH.isLoaded ? GTHooks.fluidDisplayStack(fs) : null)
        .lookupMatcher((fs, lookup) -> {
            if (!Compat.GREGTECH.isLoaded) return false;
            final FluidStack lookupFluid = GTHooks.fluidFromDisplayStack(lookup);
            return lookupFluid != null && lookupFluid.getFluid() == fs.getFluid();
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
