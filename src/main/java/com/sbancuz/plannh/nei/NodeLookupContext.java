package com.sbancuz.plannh.nei;

import java.util.UUID;

import net.minecraft.item.ItemStack;

/**
 * The node (and which of its ingredients) the user last ran an NEI recipe/usage lookup from, so
 * a recipe added from that lookup can be wired back to the originating node. Held as a one-shot
 * pending value on the canvas ({@code CanvasWidget#consumePendingLookup}), whose lifetime bounds
 * the lookup's.
 *
 * <p>
 * Fluids are covered because NEI's lookup currency is the ItemStack: a fluid port answers R/U
 * with its GT fluid display stack, and ports translate display stacks back when matching
 * ({@code Port#matchesLookup}).
 */
public record NodeLookupContext(UUID nodeId, ItemStack stack) {}
