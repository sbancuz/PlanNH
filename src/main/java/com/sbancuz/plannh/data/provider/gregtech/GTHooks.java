package com.sbancuz.plannh.data.provider.gregtech;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.items.MetaGeneratedItem;
import gregtech.api.util.GTUtility;

/** GT class references, isolated. Only call behind {@code Compat.GREGTECH.isLoaded}. */
public final class GTHooks {

    private GTHooks() {}

    /** The display item NEI shows for a fluid - the stack R/U lookups and edges operate on. */
    @Nullable
    public static ItemStack fluidDisplayStack(final FluidStack fluidStack) {
        return GTUtility.getFluidDisplayStack(fluidStack, false);
    }

    /** Reverses {@link #fluidDisplayStack}; null if the stack is not a fluid display item. */
    @Nullable
    public static FluidStack fluidFromDisplayStack(final ItemStack stack) {
        return GTUtility.getFluidFromDisplayStack(stack);
    }

    /**
     * GT material color for MetaGeneratedItems (their renderer applies it, so the item icon API
     * lies about their color); white when the stack has none.
     */
    public static int materialTint(final ItemStack stack) {
        if (stack.getItem() instanceof final MetaGeneratedItem gtItem) {
            final short[] rgba = gtItem.getRGBa(stack);
            if (rgba != null && rgba.length >= 3) {
                return (rgba[0] & 0xFF) << 16 | (rgba[1] & 0xFF) << 8 | (rgba[2] & 0xFF);
            }
        }
        return 0xFFFFFF;
    }
}
