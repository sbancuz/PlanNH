package com.sbancuz.plannh.data.flowchart;

import java.util.UUID;

import com.google.gson.JsonObject;

import lombok.Getter;
import lombok.Setter;

public abstract class GraphData implements IJsonSerializable {

    @Getter
    protected final UUID id;
    @Getter
    @Setter
    protected int x;
    @Getter
    @Setter
    protected int y;
    @Getter
    @Setter
    protected String header;
    // @Getter @Setter
    // protected int w;
    // @Getter @Setter
    // protected int h;

    protected GraphData(UUID id) {
        this.id = id;
    }

    @Override
    public void loadFromJson(JsonObject json) {
        x = json.get("x")
            .getAsInt();
        y = json.get("y")
            .getAsInt();
        header = json.get("header")
            .getAsString();
        // w = json.get("w").getAsInt();
        // h = json.get("h").getAsInt();
    }

    @Override
    public void saveToJson(JsonObject json) {
        json.addProperty("id", id.toString());
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("header", header);
        // json.addProperty("w", w);
        // json.addProperty("h", h);
    }
}
