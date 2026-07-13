package com.sbancuz.plannh.api;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.StatCollector;

import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Serializer;
import com.sbancuz.plannh.data.flowchart.SlotSet;
import com.sbancuz.plannh.nei.NodeLookupContext;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;

public final class PlanAPI {

    /** NBT key used to store the encoded graph in the share ItemStack. */
    public static final String PLANNH_DATA_KEY = "plannh_data";

    @Nullable
    private static SlotSet slotSet = null;

    private static final NodeLookupContext LOOKUP_CONTEXT = new NodeLookupContext();

    /** The session's pending NEI lookup origin (see {@link NodeLookupContext}). */
    @Nonnull
    public static NodeLookupContext lookupContext() {
        return LOOKUP_CONTEXT;
    }

    @Nonnull
    public static SlotSet getSlotSet() {
        if (slotSet == null) {
            slotSet = loadSlotSet();
        }
        return slotSet;
    }

    public static void unloadSlotSet() {
        save();
        slotSet = null;
    }

    @Nonnull
    public static Graph getActiveGraph() {
        return getSlotSet().getActiveGraph();
    }

    public static void save() {
        saveSlotSet(getSlotSet());
    }

    /**
     * Encodes the given graph and sends it as an NEI item-link chat message.
     * Other PlanNH clients see an import link; vanilla clients see a dirt-item
     * tooltip with a bookmark prompt.
     */
    public static void shareGraph(final Graph graph) {
        final String encoded = Serializer.encode(graph);
        final ItemStack stack = createShareStack();
        stack.getTagCompound()
            .setString(PLANNH_DATA_KEY, encoded);
        NEIClientUtils.sendChatItemLink(stack);
    }

    /** Copies the serialised graph to the system clipboard. */
    public static void copyToClipboard(final Graph graph) {
        GuiScreen.setClipboardString(Serializer.encode(graph));
    }

    /**
     * Reads graph data from the system clipboard and deserialises it.
     *
     * @return the deserialised graph, or {@code null} if clipboard is empty
     *         or the data is invalid.
     */
    @Nullable
    public static Graph importFromClipboard() {
        final String data = GuiScreen.getClipboardString();
        if (data.isEmpty()) return null;
        try {
            return Serializer.decode(data);
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * Extracts PlanNH share data from a serialised ItemStack NBT string and
     * deserialises it. This is the receiver side of {@link #shareGraph(Graph)}.
     *
     * @param nbtString the full serialised NBT from the /nei_bookmark command
     * @return the deserialised graph, or {@code null} if the NBT doesn't
     *         contain a {@code plannh_data} tag or the data is invalid.
     */
    @Nullable
    public static Graph importFromNBT(final String nbtString) {
        try {
            final NBTTagCompound nbt = (NBTTagCompound) JsonToNBT.func_150315_a(nbtString);
            if (!nbt.hasKey("tag")) return null;
            final NBTTagCompound tag = nbt.getCompoundTag("tag");
            if (!tag.hasKey(PLANNH_DATA_KEY)) return null;
            return Serializer.decode(tag.getString(PLANNH_DATA_KEY));
        } catch (final NBTException e) {
            return null;
        }
    }

    /**
     * Checks whether a serialised ItemStack NBT string contains PlanNH share
     * data, without fully deserialising the graph.
     */
    public static boolean containsPlanNHData(@Nonnull final String nbtString) {
        try {
            final NBTTagCompound nbt = (NBTTagCompound) JsonToNBT.func_150315_a(nbtString);
            return nbt.hasKey("tag") && nbt.getCompoundTag("tag")
                .hasKey(PLANNH_DATA_KEY);
        } catch (final NBTException e) {
            return false;
        }
    }

    /**
     * Wraps a deserialised graph in a new "Imported" slot, adds it to the
     * active slot set, switches to it, and persists. Does not open any GUI.
     */
    public static void importGraph(@Nonnull final Graph graph) {
        final SlotSet set = getSlotSet();
        final SlotSet.Slot slot = new SlotSet.Slot(StatCollector.translateToLocal("plannh.share.slot_imported"), graph);
        set.slots.add(slot);
        set.activeSlot = set.slots.size() - 1;
        save();
    }

    /**
     * Creates a dirt ItemStack with PlanNH share display properties
     * (localised name and lore). Both the sender (share button) and
     * receiver (chat hover tooltip) build the same item so the visual
     * is consistent on both sides.
     */
    @Nonnull
    public static ItemStack createShareStack() {
        final ItemStack stack = new ItemStack(Blocks.dirt);
        final NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", StatCollector.translateToLocal("plannh.share.item_name"));
        final NBTTagList lore = new NBTTagList();
        lore.appendTag(new NBTTagString(StatCollector.translateToLocal("plannh.share.lore_shared")));
        lore.appendTag(new NBTTagString(StatCollector.translateToLocal("plannh.share.click_to_import")));
        display.setTag("Lore", lore);
        stack.setTagInfo("display", display);
        return stack;
    }

    private static SlotSet loadSlotSet() {
        try {
            final File saveFile = getSaveFile();
            if (saveFile.isFile()) {
                final String data = Files.readString(saveFile.toPath(), StandardCharsets.UTF_8);
                if (data.startsWith("{")) {
                    return Serializer.decodeSlotSet(data);
                }
                final Graph graph = Serializer.decode(data);
                final SlotSet set = new SlotSet();
                set.slots.add(new SlotSet.Slot("Slot 1", graph));
                return set;
            }
        } catch (final Exception ignored) {}
        final SlotSet set = new SlotSet();
        set.slots.add(new SlotSet.Slot("Slot 1", new Graph()));
        return set;
    }

    private static void saveSlotSet(final SlotSet set) {
        try {
            final File saveFile = getSaveFile();
            saveFile.getParentFile()
                .mkdirs();
            Files.writeString(saveFile.toPath(), Serializer.encode(set), StandardCharsets.UTF_8);
        } catch (final Exception ignored) {}
    }

    private static File getSaveFile() {
        final Minecraft mc = Minecraft.getMinecraft();
        final String worldName = NEIClientConfig.getWorldPath();
        if (worldName != null && !worldName.isEmpty()) {
            return new File(mc.mcDataDir, "saves/NEI/" + worldName + "/plannh/plannh.dat");
        }
        return new File(mc.mcDataDir, "plannh/plannh.dat");
    }
}
