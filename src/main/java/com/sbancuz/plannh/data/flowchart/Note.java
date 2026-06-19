package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import lombok.Getter;
import lombok.Setter;

public class Note extends GraphData {

    @Getter
    @Setter
    private List<String> text = new ArrayList<>();

    public Note(final UUID id) {
        super(id);
        if (header == null) header = "Note";
    }

    @Override
    public void loadFromJson(JsonObject json) {
        super.loadFromJson(json);
        JsonArray jsonArray = json.getAsJsonArray("text");
        for (JsonElement elem : jsonArray) text.add(elem.getAsString());
    }

    @Override
    public void saveToJson(JsonObject json) {
        super.saveToJson(json);
        JsonArray textArray = new JsonArray();
        for (String s : text) textArray.add(new JsonPrimitive(s));
        json.add("text", textArray);
    }
}
