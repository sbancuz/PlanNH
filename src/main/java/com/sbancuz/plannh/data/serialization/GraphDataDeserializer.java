package com.sbancuz.plannh.data.serialization;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.sbancuz.plannh.data.flowchart.GraphData;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Note;

public class GraphDataDeserializer implements JsonDeserializer<GraphData> {

    @Override
    public GraphData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        return switch (json.getAsJsonObject()
            .get("type")
            .getAsString()) {
            case "note" -> Serializer.GSON.fromJson(json, Note.class);
            case "group" -> Serializer.GSON.fromJson(json, Group.class);
            case "node" -> {
                Node node = Serializer.GSON.fromJson(json, Node.class);
                node.init();
                yield node;
            }
            default -> throw new JsonParseException("Invalid type detected during GroupData deserialization");
        };
    }
}
