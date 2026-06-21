package com.sbancuz.plannh.data.flowchart;

import java.util.UUID;

import com.google.gson.JsonObject;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Getter @Setter
public abstract class GraphData implements IJsonSerializable {

    protected final UUID id;
    protected int x;
    protected int y;
    protected String header;

    protected GraphData(UUID id){
        this.id = id;
        header = StringUtils.capitalize(getType());
    }

    protected GraphData(JsonObject json){
        id = UUID.fromString(json.get("id").getAsString());
        x = json.get("x")
            .getAsInt();
        y = json.get("y")
            .getAsInt();
        header = json.get("header")
            .getAsString();
    }

    public abstract String getType();

    @Override
    public void saveToJson(JsonObject json) {
        json.addProperty("id", id.toString());
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("header", header);
        json.addProperty("type", getType());
    }

    public static GraphData loadFromJson(JsonObject json){
        String type = json.get("type").getAsString();
        return switch (type){
            case "note" -> new Note(json);
            case "group" -> new Group(json);
            default -> throw new IllegalArgumentException("Invalid type " + type);
        };
    }
}
