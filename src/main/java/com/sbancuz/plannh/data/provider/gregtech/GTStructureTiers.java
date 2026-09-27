package com.sbancuz.plannh.data.provider.gregtech;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.ItemList;

/**
 * What the coil and pipe casing rows show a player: coil heat, and the casing a pipe casing tier
 * means. How far any structure parameter goes is the machine's own declaration, not this class.
 */
public final class GTStructureTiers {

    private GTStructureTiers() {}

    /** GT counts None and ULV below Cupronickel, which is why its {@code getTier()} subtracts two. */
    public static final int MAX_COIL_TIER = HeatingCoilLevel.getMaxTier();

    /**
     * The pipe casings, weakest first, so tier 1 is Bronze. Both machines that read the setting agree on
     * this order: GT++'s Chemical Plant takes block meta 12 to 15 as tier 1 to 4, and GregTech's steam
     * multiblocks take the same two lowest metas as their tier 1 and 2.
     */
    private static final ItemList[] PIPE_CASINGS = { ItemList.Casing_Pipe_Bronze, ItemList.Casing_Pipe_Steel,
        ItemList.Casing_Pipe_Titanium, ItemList.Casing_Pipe_TungstenSteel };

    public static final int MAX_PIPE_CASING_TIER = PIPE_CASINGS.length;

    /** Filled on first use, because item display names need a registry that is empty at class load. */
    private static final String[] PIPE_CASING_NAMES = new String[PIPE_CASINGS.length];

    /** How hot a coil of this tier runs, in Kelvin. Tiers outside the range clamp to it. */
    public static int coilHeat(final int coilTier) {
        return (int) HeatingCoilLevel.getFromTier((byte) clampCoil(coilTier))
            .getHeat();
    }

    /** Into {@code [0, max]}. A stored tier from a pack with a longer table still resolves to a real one. */
    public static int clamp(final int tier, final int max) {
        return Math.max(0, Math.min(max, tier));
    }

    public static int clampCoil(final int coilTier) {
        return clamp(coilTier, MAX_COIL_TIER);
    }

    /**
     * GregTech's own name for the casing at a pipe casing tier, so the row names the block a player
     * places rather than a number only the code uses. Falls back to the tier when the item registry
     * has nothing, which is what a headless run sees.
     */
    @Nonnull
    public static String pipeCasingName(final int tier) {
        final int index = Math.max(1, Math.min(MAX_PIPE_CASING_TIER, tier)) - 1;
        if (PIPE_CASING_NAMES[index] == null) {
            PIPE_CASING_NAMES[index] = readItemName(PIPE_CASINGS[index], String.valueOf(index + 1));
        }
        return PIPE_CASING_NAMES[index];
    }

    /** The casing kind, which every row that shows one of these has already said in its own label. */
    private static final String PIPE_CASING_SUFFIX = " Pipe Casing";

    /**
     * The material rather than the whole item name, so a row reads "Pipe Casing Tungstensteel" the way
     * a coil row reads "Coil HSS-S" - GregTech names a coil by its material already, and names a
     * casing by material and kind together. A locale that words it differently keeps the full name,
     * which is long but never wrong.
     */
    @Nonnull
    private static String readItemName(final ItemList item, final String fallback) {
        try {
            final ItemStack stack = item.get(1);
            if (stack == null) return fallback;
            final String name = stack.getDisplayName();
            return name.endsWith(PIPE_CASING_SUFFIX) ? name.substring(0, name.length() - PIPE_CASING_SUFFIX.length())
                : name;
        } catch (final RuntimeException | LinkageError e) {
            return fallback;
        }
    }
}
