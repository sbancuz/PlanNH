package com.sbancuz.plannh.api;

import java.io.File;
import java.nio.file.Files;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;

import com.sbancuz.plannh.data.FlowchartGraph;
import com.sbancuz.plannh.data.FlowchartSerializer;
import com.sbancuz.plannh.gui.FlowchartGuiContainer;
import com.sbancuz.plannh.gui.FlowchartScreen;

import codechicken.nei.NEIClientConfig;

public class PlanAPI {

    public static @Nonnull FlowchartGraph getGraph(@Nonnull GuiContainer gui) {
        if (gui instanceof FlowchartGuiContainer fGui) {
            if (fGui.getScreen() instanceof FlowchartScreen screen) {
                return screen.graph;
            }
        }
        return reloadFromFile();
    }

    public static @Nonnull FlowchartGraph reloadFromFile() {
        try {
            File saveFile = getSaveFile();
            if (saveFile.isFile()) {
                String data = Files.readString(saveFile.toPath());
                return FlowchartSerializer.fromBase64(data);
            }
        } catch (Exception ignored) {}
        return new FlowchartGraph();
    }

    public static void saveGraph(@Nonnull FlowchartGraph graph) {
        try {
            File saveFile = getSaveFile();
            saveFile.getParentFile()
                .mkdirs();
            String data = FlowchartSerializer.toBase64(graph);
            Files.writeString(saveFile.toPath(), data);
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
