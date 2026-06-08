package com.sbancuz.plannh.api;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraft.client.Minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Serializer;
import com.sbancuz.plannh.data.flowchart.SlotSet;

import codechicken.nei.NEIClientConfig;

public class PlanAPI {

    private static SlotSet slotSet = null;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    public static SlotSet getSlotSet() {
        if (slotSet == null) {
            slotSet = loadSlotSet();
        }
        return slotSet;
    }

    public static Graph getActiveGraph() {
        return getSlotSet().getActiveGraph();
    }

    public static void save() {
        saveSlotSet(getSlotSet());
    }

    private static SlotSet loadSlotSet() {
        try {
            File saveFile = getSaveFile();
            if (saveFile.isFile()) {
                String data = Files.readString(saveFile.toPath(), StandardCharsets.UTF_8);
                if (data.startsWith("{")) {
                    JsonObject root = GSON.fromJson(data, JsonObject.class);
                    return parseSlotSet(root);
                }
                Graph graph = Serializer.fromBase64(data);
                SlotSet set = new SlotSet();
                set.slots.add(new SlotSet.Slot("Slot 1", graph));
                return set;
            }
        } catch (Exception ignored) {}
        SlotSet set = new SlotSet();
        set.slots.add(new SlotSet.Slot("Slot 1", new Graph()));
        return set;
    }

    private static void saveSlotSet(SlotSet set) {
        try {
            File saveFile = getSaveFile();
            saveFile.getParentFile()
                .mkdirs();
            JsonObject root = new JsonObject();
            root.addProperty("active", set.activeSlot);
            JsonArray arr = new JsonArray();
            for (SlotSet.Slot slot : set.slots) {
                JsonObject slotObj = new JsonObject();
                slotObj.addProperty("name", slot.name);
                slotObj.addProperty("data", Serializer.toBase64(slot.graph));
                arr.add(slotObj);
            }
            root.add("slots", arr);
            Files.writeString(saveFile.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private static SlotSet parseSlotSet(JsonObject root) {
        SlotSet set = new SlotSet();
        set.activeSlot = root.get("active")
            .getAsInt();
        JsonArray arr = root.getAsJsonArray("slots");
        for (JsonElement elem : arr) {
            JsonObject obj = elem.getAsJsonObject();
            String name = obj.get("name")
                .getAsString();
            String data = obj.get("data")
                .getAsString();
            Graph graph = Serializer.fromBase64(data);
            set.slots.add(new SlotSet.Slot(name, graph));
        }
        return set;
    }

    private static File getSaveFile() {
        Minecraft mc = Minecraft.getMinecraft();
        String worldName = NEIClientConfig.getWorldPath();
        if (worldName != null && !worldName.isEmpty()) {
            return new File(mc.mcDataDir, "saves/NEI/" + worldName + "/plannh/plannh.dat");
        }
        return new File(mc.mcDataDir, "plannh/plannh.dat");
    }
}
