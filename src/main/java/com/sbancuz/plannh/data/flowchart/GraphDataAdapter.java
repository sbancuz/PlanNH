package com.sbancuz.plannh.data.flowchart;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

public class GraphDataAdapter implements JsonDeserializer<GraphData> {

    @Override
    public GraphData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        return switch (json.getAsJsonObject()
            .get("type")
            .getAsString()) {
            case "note" -> Serializer.GSON.fromJson(json, Note.class);
            case "group" -> Serializer.GSON.fromJson(json, Group.class);
            default -> throw new IllegalArgumentException("Invalid type detected during GroupData deserialization");
        };
    }
}
