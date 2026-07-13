package com.sbancuz.plannh.nei;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

/**
 * Remembers which node (and which of its ingredients) the user last ran an NEI recipe/usage
 * lookup from, so a recipe added from that lookup can be wired back to the originating node
 * automatically.
 */
public final class NodeLookupContext {

    @Nullable
    private static UUID nodeId;
    @Nullable
    private static ItemStack stack;

    private NodeLookupContext() {}

    public static void set(final UUID originNodeId, final ItemStack lookupStack) {
        nodeId = originNodeId;
        stack = lookupStack;
    }

    @Nullable
    public static UUID nodeId() {
        return nodeId;
    }

    @Nullable
    public static ItemStack stack() {
        return stack;
    }
}
