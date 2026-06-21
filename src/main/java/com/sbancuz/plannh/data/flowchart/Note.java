package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class Note extends GraphData {

    @NotNull
    private List<String> text = new ArrayList<>();

    public Note() {
        super(UUID.randomUUID());
    }

    public Note(JsonObject json){
        super(json);
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

    @Override
    public String getType() {
        return "note";
    }
}
