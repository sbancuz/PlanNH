package com.sbancuz.plannh.api;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraft.client.Minecraft;

import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Serializer;
import com.sbancuz.plannh.data.flowchart.SlotSet;

import codechicken.nei.NEIClientConfig;

public class PlanAPI {

    private static SlotSet slotSet = null;

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
                    return Serializer.decodeSlotSet(data);
                }
                Graph graph = Serializer.decode(data);
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
            Files.writeString(saveFile.toPath(), Serializer.encode(set), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
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
