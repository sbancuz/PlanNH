package com.sbancuz.plannh.data.flowchart;

import java.util.UUID;

import com.google.gson.JsonObject;

import lombok.Data;

@Data
public abstract class GraphData implements IJsonSerializable {

    protected final UUID id;
    protected int x;
    protected int y;
    protected String header;

    @Override
    public void loadFromJson(JsonObject json) {
        x = json.get("x")
            .getAsInt();
        y = json.get("y")
            .getAsInt();
        header = json.get("header")
            .getAsString();
    }

    @Override
    public void saveToJson(JsonObject json) {
        json.addProperty("id", id.toString());
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("header", header);
    }
}
