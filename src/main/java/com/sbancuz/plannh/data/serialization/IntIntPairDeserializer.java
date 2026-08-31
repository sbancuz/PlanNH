package com.sbancuz.plannh.data.serialization;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import it.unimi.dsi.fastutil.ints.IntIntPair;

public class IntIntPairDeserializer implements JsonDeserializer<IntIntPair> {

    @Override
    public IntIntPair deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        return IntIntPair.of(
            obj.get("left")
                .getAsInt(),
            obj.get("right")
                .getAsInt());
    }
}
