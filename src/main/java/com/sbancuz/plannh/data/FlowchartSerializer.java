package com.sbancuz.plannh.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.item.ItemStack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class FlowchartSerializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    public static String toBase64(FlowchartGraph graph) {
        try {
            JsonObject root = new JsonObject();
            JsonArray nodesArray = new JsonArray();
            for (FlowchartNode node : graph.getNodes()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", node.id.toString());
                obj.addProperty("x", node.x);
                obj.addProperty("y", node.y);
                obj.addProperty("machine", node.machineName);
                obj.addProperty("ticks", node.durationTicks);
                obj.addProperty("eu", node.totalEu);
                obj.addProperty("recipeOwner", node.recipeOwner);
                obj.addProperty("handlerRecipeIndex", node.handlerRecipeIndex);
                obj.add("inputs", itemStackArrayToJson(node.inputs));
                obj.add("outputs", itemStackArrayToJson(node.outputs));
                nodesArray.add(obj);
            }
            root.add("nodes", nodesArray);

            JsonArray edgesArray = new JsonArray();
            for (FlowchartEdge edge : graph.getEdges()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id", edge.id.toString());
                obj.addProperty("src", edge.sourceNodeId.toString());
                obj.addProperty("dst", edge.targetNodeId.toString());
                obj.addProperty("srcOut", edge.sourceOutputIndex);
                obj.addProperty("dstIn", edge.targetInputIndex);
                edgesArray.add(obj);
            }
            root.add("edges", edgesArray);

            String json = GSON.toJson(root);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos);
                OutputStreamWriter writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            return Base64.getEncoder()
                .encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize flowchart", e);
        }
    }

    public static FlowchartGraph fromBase64(String data) {
        try {
            byte[] bytes = Base64.getDecoder()
                .decode(data);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            StringBuilder json = new StringBuilder();
            try (GZIPInputStream gzip = new GZIPInputStream(bais);
                InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
                char[] buf = new char[4096];
                int len;
                while ((len = reader.read(buf)) != -1) {
                    json.append(buf, 0, len);
                }
            }

            JsonObject root = GSON.fromJson(json.toString(), JsonObject.class);
            FlowchartGraph graph = new FlowchartGraph();

            JsonArray nodesArray = root.getAsJsonArray("nodes");
            for (JsonElement elem : nodesArray) {
                JsonObject obj = elem.getAsJsonObject();
                UUID id = UUID.fromString(
                    obj.get("id")
                        .getAsString());
                int x = obj.get("x")
                    .getAsInt();
                int y = obj.get("y")
                    .getAsInt();
                FlowchartNode node = new FlowchartNode(id, x, y);
                node.machineName = obj.get("machine")
                    .getAsString();
                node.durationTicks = obj.get("ticks")
                    .getAsInt();
                node.totalEu = obj.has("eu") ? obj.get("eu")
                    .getAsLong() : 0;
                node.recipeOwner = obj.has("recipeOwner") ? obj.get("recipeOwner")
                    .getAsString() : "";
                node.handlerRecipeIndex = obj.has("handlerRecipeIndex") ? obj.get("handlerRecipeIndex")
                    .getAsInt() : 0;
                jsonArrayToItemStacks(obj.getAsJsonArray("inputs"), node.inputs);
                jsonArrayToItemStacks(obj.getAsJsonArray("outputs"), node.outputs);
                graph.addNode(node);
            }

            JsonArray edgesArray = root.getAsJsonArray("edges");
            for (JsonElement elem : edgesArray) {
                JsonObject obj = elem.getAsJsonObject();
                UUID id = UUID.fromString(
                    obj.get("id")
                        .getAsString());
                UUID src = UUID.fromString(
                    obj.get("src")
                        .getAsString());
                UUID dst = UUID.fromString(
                    obj.get("dst")
                        .getAsString());
                int srcOut = obj.get("srcOut")
                    .getAsInt();
                int dstIn = obj.get("dstIn")
                    .getAsInt();
                graph.addEdge(new FlowchartEdge(id, src, dst, srcOut, dstIn));
            }

            return graph;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize flowchart", e);
        }
    }

    private static JsonArray itemStackArrayToJson(java.util.List<ItemStack> stacks) {
        JsonArray arr = new JsonArray();
        for (ItemStack stack : stacks) {
            if (stack == null) continue;
            JsonObject obj = new JsonObject();
            obj.addProperty("id", net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
            obj.addProperty("size", stack.stackSize);
            obj.addProperty("meta", stack.getItemDamage());
            if (stack.hasTagCompound()) {
                obj.addProperty(
                    "nbt",
                    stack.getTagCompound()
                        .toString());
            }
            arr.add(obj);
        }
        return arr;
    }

    private static void jsonArrayToItemStacks(JsonArray arr, java.util.List<ItemStack> out) {
        for (JsonElement elem : arr) {
            JsonObject obj = elem.getAsJsonObject();
            String itemId = obj.get("id")
                .getAsString();
            int size = obj.get("size")
                .getAsInt();
            int meta = obj.get("meta")
                .getAsInt();
            ItemStack stack = new ItemStack(
                (net.minecraft.item.Item) net.minecraft.item.Item.itemRegistry.getObject(itemId),
                size,
                meta);
            if (obj.has("nbt")) {
                try {
                    stack.setTagCompound(
                        (net.minecraft.nbt.NBTTagCompound) net.minecraft.nbt.JsonToNBT.func_150315_a(
                            obj.get("nbt")
                                .getAsString()));
                } catch (net.minecraft.nbt.NBTException ignored) {}
            }
            out.add(stack);
        }
    }
}
