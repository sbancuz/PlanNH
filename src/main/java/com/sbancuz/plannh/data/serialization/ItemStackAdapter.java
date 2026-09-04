package com.sbancuz.plannh.data.serialization;

import java.lang.reflect.Type;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;

public class ItemStackAdapter implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {

    @Override
    public ItemStack deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        return StackInfo.loadFromNBT((NBTTagCompound) NBTJson.toNbt(json));
    }

    @Override
    public JsonElement serialize(ItemStack src, Type typeOfSrc, JsonSerializationContext context) {
        return NBTJson.toJsonObject(StackInfo.itemStackToNBT(src));
    }
}
