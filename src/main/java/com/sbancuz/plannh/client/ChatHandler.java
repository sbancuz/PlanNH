package com.sbancuz.plannh.client;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

import com.sbancuz.plannh.api.PlanAPI;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Intercepts incoming NEI item-link chat messages and, if the item's NBT
 * contains PlanNH share data, replaces the message with a clickable import
 * link that opens the shared flowchart on the receiver's client.
 *
 * The share format uses NEI's built-in Ctrl+L item sharing so that players
 * without PlanNH see a normal dirt-item hover tooltip and harmless NEI
 * bookmark behaviour, while PlanNH clients get a seamless import action.
 */
public class ChatHandler {

    /** The command prefix NEI uses for Ctrl+L item-link click events. */
    private static final String BOOKMARK_COMMAND = "/nei_bookmark ";

    /**
     * Fired on the client for every incoming chat message. We only act on
     * NEI item-link messages ({@code nei.chat.item_link.text}) whose item
     * NBT carries a {@code plannh_data} tag.
     */
    @SubscribeEvent
    public void onClientChatReceived(final ClientChatReceivedEvent event) {
        if (!(event.message instanceof ChatComponentTranslation msg)) return;

        if (!"nei.chat.item_link.text".equals(msg.getKey())) return;

        final Object[] args = msg.getFormatArgs();
        if (args.length < 2 || !(args[1] instanceof IChatComponent itemComponent)) return;

        final ClickEvent click = itemComponent.getChatStyle()
            .getChatClickEvent();
        if (click == null || click.getAction() != ClickEvent.Action.RUN_COMMAND) return;

        final String value = click.getValue();
        if (!value.startsWith(BOOKMARK_COMMAND)) return;

        final String nbtString = value.substring(BOOKMARK_COMMAND.length());
        if (!PlanAPI.containsPlanNHData(nbtString)) return;

        final IChatComponent replacement = new ChatComponentTranslation("plannh.share.import_link");
        replacement.getChatStyle()
            .setColor(EnumChatFormatting.AQUA);
        replacement.getChatStyle()
            .setBold(true);
        replacement.getChatStyle()
            .setUnderlined(true);

        final IChatComponent component = new ChatComponentText(replacement.getFormattedText());
        final ChatStyle style = component.getChatStyle();
        style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new ChatComponentText(fakeItemNbt())));
        style.setChatClickEvent(
            new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + ImportCommand.COMMAND_NAME + " " + nbtString));
        component.setChatStyle(style);
        event.message = component;
    }

    /**
     * Builds the NBT string for the fake dirt item shown on hover.
     */
    private static String fakeItemNbt() {
        final ItemStack stack = PlanAPI.createShareStack();
        final NBTTagCompound tag = new NBTTagCompound();
        stack.writeToNBT(tag);
        return tag.toString();
    }
}
