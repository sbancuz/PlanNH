package com.sbancuz.plannh.data.flowchart;

import java.lang.reflect.Type;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public final class ItemStackAdapter implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {

    @Override
    public JsonElement serialize(final ItemStack src, final Type typeOfSrc, final JsonSerializationContext context) {
        final JsonObject obj = new JsonObject();
        obj.addProperty("itemId", Item.getIdFromItem(src.getItem()));
        obj.addProperty("itemDamage", src.getItemDamage());
        obj.addProperty("itemSize", src.stackSize);
        return obj;
    }

    @Override
    public ItemStack deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
        throws JsonParseException {
        if (json.isJsonNull()) return null;
        final JsonObject obj = json.getAsJsonObject();
        if (!obj.has("itemId")) return null;
        final int itemId = obj.get("itemId")
            .getAsInt();
        final int damage = obj.get("itemDamage")
            .getAsInt();
        final int size = obj.get("itemSize")
            .getAsInt();
        final Item item = Item.getItemById(itemId);
        return item != null ? new ItemStack(item, size, damage) : null;
    }
}
