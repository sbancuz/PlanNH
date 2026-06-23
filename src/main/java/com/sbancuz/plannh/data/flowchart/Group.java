package com.sbancuz.plannh.data.flowchart;

import static com.sbancuz.plannh.gui.GroupWidget2.GROUP_MIN_H;
import static com.sbancuz.plannh.gui.GroupWidget2.GROUP_MIN_W;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.utils.Color;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Group extends GraphData {

    private static Random colorRandom = new Random(12345);

    private int width = GROUP_MIN_W;
    private int height = GROUP_MIN_H;
    private int color = getRandomColor();
    private boolean collapsed;
    private boolean clampNodes;
    private boolean coverChildren;
    @NotNull
    private final Map<UUID, GraphData> children = new HashMap<>();

    public Group() {
        super(UUID.randomUUID());
    }

    public Group(JsonObject json) {
        super(json);
        width = json.get("width")
            .getAsInt();
        height = json.get("height")
            .getAsInt();
        color = json.get("color")
            .getAsInt();
        collapsed = json.get("collapsed")
            .getAsBoolean();
        clampNodes = json.get("clampNodes")
            .getAsBoolean();
        coverChildren = json.get("coverChildren")
            .getAsBoolean();
        for (JsonElement elem : json.getAsJsonArray("children")) {
            JsonObject obj = elem.getAsJsonObject();
            children.put(
                UUID.fromString(
                    obj.get("id")
                        .getAsString()),
                GraphData.loadFromJson(
                    obj.get("data")
                        .getAsJsonObject()));
        }
    }

    @Override
    public void saveToJson(JsonObject json) {
        super.saveToJson(json);
        json.addProperty("width", width);
        json.addProperty("height", height);
        json.addProperty("color", color);
        json.addProperty("collapsed", collapsed);
        json.addProperty("clampNodes", clampNodes);
        json.addProperty("coverChildren", coverChildren);
        JsonArray jsonArray = new JsonArray();
        for (Map.Entry<UUID, GraphData> entry : children.entrySet()) {
            JsonObject child = new JsonObject();
            child.addProperty(
                "id",
                entry.getKey()
                    .toString());
            JsonObject data = new JsonObject();
            entry.getValue()
                .saveToJson(data);
            child.add("data", data);
            jsonArray.add(child);
        }
        json.add("children", jsonArray);
    }

    @Override
    public String getType() {
        return "group";
    }

    private int getRandomColor() {
        return Color.argb(colorRandom.nextFloat(), colorRandom.nextFloat(), colorRandom.nextFloat(), 0.5f);
    }
}
