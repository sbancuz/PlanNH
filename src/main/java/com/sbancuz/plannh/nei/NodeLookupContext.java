package com.sbancuz.plannh.nei;

import java.util.UUID;

/**
 * The port the user last ran an NEI recipe/usage lookup from (R/U on a node's pin, throughput
 * row, or embedded recipe stack), so a recipe added from that lookup can be wired back to it.
 * Held as a one-shot pending value on the canvas ({@code CanvasWidget#consumePendingLookup}),
 * whose lifetime bounds the lookup's.
 *
 * <p>
 * Deliberately a port handle, not an ItemStack: stacks are NEI's lookup currency and stay at
 * that boundary ({@code RecipeNodeWidget#getStackForRecipeViewer}). Wiring back happens in
 * port/value space via {@code Port#canConnect}, which every resource type supports without
 * ItemStack reverse-mapping.
 */
public record NodeLookupContext(UUID nodeId, boolean output, int portIndex) {}
