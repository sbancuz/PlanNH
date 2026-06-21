package com.sbancuz.plannh.data.flowchart;

import com.cleanroommc.modularui.utils.Color;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Data
public class Group extends GraphData {

    private static final int DEFAULT_W = 300;
    private static final int DEFAULT_H = 200;

    private int w = DEFAULT_W;
    private int h = DEFAULT_H;
    private int color = getRandomColor();
    private boolean collapsed;
    private boolean clampNodes;
    private boolean autoResize;
    @NotNull
    private final Map<UUID, GraphData> children = new HashMap<>();

    public Group() {
        super(UUID.randomUUID());
    }

    public Group(JsonObject json){
        super(json);
        w = json.get("w").getAsInt();
        h = json.get("h").getAsInt();
        color = json.get("color").getAsInt();
        collapsed = json.get("collapsed").getAsBoolean();
        clampNodes = json.get("clampNodes").getAsBoolean();
        autoResize = json.get("autoResize").getAsBoolean();
        for(JsonElement elem : json.getAsJsonArray("children")){
            JsonObject obj = elem.getAsJsonObject();
            children.put(UUID.fromString(obj.get("key").getAsString()), GraphData.loadFromJson(obj.get("value").getAsJsonObject()));
        }
    }

    @Override
    public void saveToJson(JsonObject json) {
        super.saveToJson(json);
        json.addProperty("w", w);
        json.addProperty("h", h);
        json.addProperty("color", color);
        json.addProperty("collapsed", collapsed);
        json.addProperty("clampNodes", clampNodes);
        json.addProperty("autoResize", autoResize);
        JsonArray jsonArray = new JsonArray();
        for(Map.Entry<UUID, GraphData> entry : children.entrySet()){
            JsonObject child = new JsonObject();
            child.addProperty("key", entry.getKey().toString());
            JsonObject value = new JsonObject();
            entry.getValue().saveToJson(value);
            child.add("value", value);
            jsonArray.add(child);
        }
        json.add("children", jsonArray);
    }

    @Override
    public String getType() {
        return "group";
    }

    private int getRandomColor() {
        Random random = new Random();
        return Color.argb(random.nextFloat(), random.nextFloat(), random.nextFloat(), 0.5f);
    }
}
