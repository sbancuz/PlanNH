package com.sbancuz.plannh.nei;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

/**
 * Remembers which node (and which of its ingredients) the user last ran an NEI recipe/usage
 * lookup from, so a recipe added from that lookup can be wired back to the originating node
 * automatically. Owned by {@link com.sbancuz.plannh.api.PlanAPI#lookupContext()}: the writer
 * (the node widget answering the R/U keybind) and the reader (the NEI overlay handler) have no
 * reference to each other and NEI constructs its handlers itself, so the shared session instance
 * lives on the API facade.
 *
 * <p>
 * Fluids are covered because NEI's lookup currency is the ItemStack: a fluid port answers R/U
 * with its GT fluid display stack, and the overlay handler translates display stacks back to
 * fluids when matching ports.
 */
public final class NodeLookupContext {

    /** The node and ingredient an NEI lookup started from. */
    public record Origin(UUID nodeId, ItemStack stack) {}

    @Nullable
    private Origin origin;

    public void set(final UUID originNodeId, final ItemStack lookupStack) {
        origin = new Origin(originNodeId, lookupStack);
    }

    /**
     * Returns the pending origin and clears it: a lookup wires at most one added recipe, so a
     * stale origin can never auto-connect something added much later in the session.
     */
    @Nullable
    public Origin consume() {
        final Origin result = origin;
        origin = null;
        return result;
    }
}
