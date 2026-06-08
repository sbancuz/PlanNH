package com.sbancuz.plannh.data.flowchart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.ExtractedProperties;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.SettingDef;

import codechicken.nei.recipe.Recipe;
import it.unimi.dsi.fastutil.objects.ObjectFloatImmutablePair;

public class Serializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    // ── Public API ──

    /** Encodes a full graph to a compressed base64 string (gzip + json + base64). */
    public static String encode(Graph graph) {
        try {
            String json = GSON.toJson(graphToJson(graph));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos);
                OutputStreamWriter writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            return Base64.getEncoder()
                .encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode flowchart", e);
        }
    }

    /** Decodes a compressed base64 string back to a Graph. */
    public static Graph decode(String data) {
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
            return jsonToGraph(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode flowchart", e);
        }
    }

    // ── SlotSet serialization ──

    /** Encodes a SlotSet (with all its graphs) to a JSON string. */
    public static String encode(SlotSet set) {
        JsonObject root = new JsonObject();
        root.addProperty("active", set.activeSlot);
        root.addProperty("summaryX", set.summaryX);
        root.addProperty("summaryY", set.summaryY);
        root.addProperty("summaryCollapsed", set.summaryCollapsed);
        JsonArray arr = new JsonArray();
        for (SlotSet.Slot slot : set.slots) {
            JsonObject slotObj = new JsonObject();
            slotObj.addProperty("name", slot.name);
            slotObj.addProperty("data", encode(slot.graph));
            arr.add(slotObj);
        }
        root.add("slots", arr);
        return GSON.toJson(root);
    }

    /** Decodes a SlotSet (with all its graphs) from a JSON string. */
    public static SlotSet decodeSlotSet(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        SlotSet set = new SlotSet();
        set.activeSlot = root.get("active")
            .getAsInt();
        set.summaryX = root.has("summaryX") ? root.get("summaryX")
            .getAsInt() : 210;
        set.summaryY = root.has("summaryY") ? root.get("summaryY")
            .getAsInt() : 46;
        set.summaryCollapsed = root.has("summaryCollapsed") && root.get("summaryCollapsed")
            .getAsBoolean();
        JsonArray arr = root.getAsJsonArray("slots");
        for (JsonElement elem : arr) {
            JsonObject obj = elem.getAsJsonObject();
            String name = obj.get("name")
                .getAsString();
            String data = obj.get("data")
                .getAsString();
            Graph graph = decode(data);
            set.slots.add(new SlotSet.Slot(name, graph));
        }
        return set;
    }

    /** Renders a graph as a Mermaid.js flowchart (LR layout). */
    public static String toMermaid(Graph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");

        // Map UUIDs to short mermaid-safe IDs
        for (Node node : graph.getNodes()) {
            String id = mermaidId(node.id);
            String label = node.machineName.isEmpty() ? "?" : node.machineName;
            sb.append("    ")
                .append(id)
                .append("[\"")
                .append(escapeMermaid(label))
                .append("\"]\n");
        }

        for (Edge edge : graph.getEdges()) {
            String srcId = mermaidId(edge.sourceNodeId);
            String dstId = mermaidId(edge.targetNodeId);
            String label = edgeLabel(graph, edge);
            sb.append("    ")
                .append(srcId)
                .append(" -->");
            if (!label.isEmpty()) {
                sb.append("|\"")
                    .append(escapeMermaid(label))
                    .append("\"|");
            }
            sb.append(" ")
                .append(dstId)
                .append("\n");
        }

        for (Note note : graph.notes.values()) {
            sb.append("    %% Note: ")
                .append(escapeMermaid(note.text))
                .append("\n");
        }

        return sb.toString();
    }

    private static JsonObject graphToJson(Graph graph) {
        JsonObject root = new JsonObject();

        JsonArray nodesArray = new JsonArray();
        for (Node node : graph.getNodes()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", node.id.toString());
            obj.addProperty("x", node.x);
            obj.addProperty("y", node.y);
            obj.addProperty("machine", node.machineName);
            obj.addProperty("ticks", node.durationTicks);
            obj.add("recipeId", node.recipeId.toJsonObject());
            obj.addProperty("handlerRecipeIndex", node.handlerRecipeIndex);
            obj.add("inputs", itemStackArrayToJson(node.inputs));
            obj.add("outputs", itemStackArrayToJson(node.outputs));
            obj.add("fluidInputs", fluidStackArrayToJson(node.fluidInputs));
            obj.add("fluidOutputs", fluidStackArrayToJson(node.fluidOutputs));

            if (node.machineConfig.hasAnyBoost()) {
                obj.add("machineConfig", machineConfigToJson(node.machineConfig));
            }

            if (!node.properties.isEmpty()) {
                JsonObject propsObj = new JsonObject();
                for (Map.Entry<RecipeProperty<?>, Object> entry : node.properties.entrySet()) {
                    serializeProperty(propsObj, entry.getKey(), entry.getValue());
                }
                obj.add("properties", propsObj);
            }

            nodesArray.add(obj);
        }
        root.add("nodes", nodesArray);

        JsonArray edgesArray = new JsonArray();
        for (Edge edge : graph.getEdges()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", edge.id.toString());
            obj.addProperty("src", edge.sourceNodeId.toString());
            obj.addProperty("dst", edge.targetNodeId.toString());
            obj.addProperty("srcOut", edge.sourceOutputIndex);
            obj.addProperty("dstIn", edge.targetInputIndex);
            edgesArray.add(obj);
        }
        root.add("edges", edgesArray);

        JsonArray notesArray = new JsonArray();
        for (Note note : graph.notes.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", note.id.toString());
            obj.addProperty("x", note.x);
            obj.addProperty("y", note.y);
            obj.addProperty("text", note.text);
            notesArray.add(obj);
        }
        root.add("notes", notesArray);

        return root;
    }

    private static Graph jsonToGraph(JsonObject root) {
        Graph graph = new Graph();

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
            Node node = new Node(id, x, y);
            node.machineName = obj.get("machine")
                .getAsString();
            node.durationTicks = obj.get("ticks")
                .getAsInt();
            node.recipeId = Recipe.RecipeId.of(
                obj.get("recipeId")
                    .getAsJsonObject());
            node.handlerRecipeIndex = obj.has("handlerRecipeIndex") ? obj.get("handlerRecipeIndex")
                .getAsInt() : 0;
            jsonArrayToItemStacks(obj.getAsJsonArray("inputs"), node.inputs);
            jsonArrayToItemStacks(obj.getAsJsonArray("outputs"), node.outputs);
            if (obj.has("fluidInputs")) jsonArrayToFluidStacks(obj.getAsJsonArray("fluidInputs"), node.fluidInputs);
            if (obj.has("fluidOutputs")) jsonArrayToFluidStacks(obj.getAsJsonArray("fluidOutputs"), node.fluidOutputs);

            if (obj.has("machineConfig")) {
                jsonToMachineConfig(obj.getAsJsonObject("machineConfig"), node.machineConfig);
            }

            if (obj.has("properties")) {
                JsonObject propsObj = obj.getAsJsonObject("properties");
                for (RecipeProperty<?> prop : RecipePropertyAPI.getProperties()) {
                    Object value = prop.deserialize(propsObj);
                    if (value != null && !value.equals(prop.getDefaultValue())) {
                        setProperty(node.properties, prop, value);
                    }
                }
            }

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
            graph.addEdge(new Edge(id, src, dst, srcOut, dstIn));
        }

        if (root.has("notes")) {
            JsonArray notesArray = root.getAsJsonArray("notes");
            for (JsonElement elem : notesArray) {
                JsonObject obj = elem.getAsJsonObject();
                UUID id = UUID.fromString(
                    obj.get("id")
                        .getAsString());
                int x = obj.get("x")
                    .getAsInt();
                int y = obj.get("y")
                    .getAsInt();
                Note note = new Note(id, x, y);
                if (obj.has("text")) note.text = obj.get("text")
                    .getAsString();
                graph.notes.put(id, note);
            }
        }

        return graph;
    }

    // ── Item / Fluid stack helpers ──

    private static JsonArray itemStackArrayToJson(List<ObjectFloatImmutablePair<ItemStack>> stacks) {
        JsonArray arr = new JsonArray();
        for (var pair : stacks) {
            if (pair.left() == null) continue;
            ItemStack stack = pair.left();
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
            obj.addProperty("chance", pair.rightFloat());
            arr.add(obj);
        }
        return arr;
    }

    private static void jsonArrayToItemStacks(JsonArray arr, List<ObjectFloatImmutablePair<ItemStack>> out) {
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
            out.add(
                new ObjectFloatImmutablePair<>(
                    stack,
                    obj.get("chance")
                        .getAsFloat()));
        }
    }

    private static JsonArray fluidStackArrayToJson(List<ObjectFloatImmutablePair<FluidStack>> fluids) {
        JsonArray arr = new JsonArray();
        for (var pair : fluids) {
            FluidStack fs = pair.left();
            if (fs == null || fs.getFluid() == null) continue;
            JsonObject obj = new JsonObject();
            obj.addProperty("fluid", FluidRegistry.getFluidName(fs.getFluid()));
            obj.addProperty("amount", fs.amount);
            obj.addProperty("chance", pair.rightFloat());
            arr.add(obj);
        }
        return arr;
    }

    private static void jsonArrayToFluidStacks(JsonArray arr, List<ObjectFloatImmutablePair<FluidStack>> out) {
        for (JsonElement elem : arr) {
            JsonObject obj = elem.getAsJsonObject();
            String name = obj.get("fluid")
                .getAsString();
            int amount = obj.get("amount")
                .getAsInt();
            FluidStack fs = FluidRegistry.getFluidStack(name, amount);
            if (fs != null) out.add(
                new ObjectFloatImmutablePair<>(
                    fs,
                    obj.get("chance")
                        .getAsFloat()));
        }
    }

    // ── Machine config ──

    private static JsonObject machineConfigToJson(MachineConfig cfg) {
        JsonObject obj = new JsonObject();
        MachineProfile profile = cfg.getProfile();

        if (!MachineProfileRegistry.defaultId()
            .equals(cfg.profileId)) {
            obj.addProperty("profile", cfg.profileId);
        }

        if (profile != null) {
            JsonObject settingsObj = new JsonObject();
            for (SettingDef<?> def : profile.settings()) {
                Object val = cfg.settings.get(def.key);
                if (val == null) continue;
                if (val.equals(def.defaultValue)) continue;
                if (val instanceof Boolean b) settingsObj.addProperty(def.key, b);
                else if (val instanceof Integer i) settingsObj.addProperty(def.key, i);
                else if (val instanceof String s) settingsObj.addProperty(def.key, s);
            }
            if (!settingsObj.entrySet()
                .isEmpty()) obj.add("settings", settingsObj);
        }

        if (!cfg.inputConsumption.isEmpty()) {
            obj.add("inMul", multiplierArrayToJson(cfg.inputConsumption));
        }
        if (!cfg.outputProductivity.isEmpty()) {
            obj.add("outMul", multiplierArrayToJson(cfg.outputProductivity));
        }

        return obj;
    }

    private static void jsonToMachineConfig(JsonObject obj, MachineConfig cfg) {
        if (obj.has("profile")) {
            cfg.profileId = obj.get("profile")
                .getAsString();
            if (obj.has("settings")) {
                JsonObject settingsObj = obj.getAsJsonObject("settings");
                for (Map.Entry<String, JsonElement> entry : settingsObj.entrySet()) {
                    JsonElement el = entry.getValue();
                    if (el.isJsonPrimitive()) {
                        var prim = el.getAsJsonPrimitive();
                        if (prim.isBoolean()) cfg.settings.put(entry.getKey(), prim.getAsBoolean());
                        else if (prim.isNumber()) cfg.settings.put(entry.getKey(), prim.getAsInt());
                        else cfg.settings.put(entry.getKey(), prim.getAsString());
                    }
                }
            }
        }

        cfg.initDefaults();

        if (obj.has("inMul")) {
            jsonToMultiplierArray(obj.getAsJsonArray("inMul"), cfg.inputConsumption);
        }
        if (obj.has("outMul")) {
            jsonToMultiplierArray(obj.getAsJsonArray("outMul"), cfg.outputProductivity);
        }
    }

    // ── Multiplier helpers ──

    private static JsonArray multiplierArrayToJson(Map<Integer, Float> map) {
        JsonArray arr = new JsonArray();
        for (Map.Entry<Integer, Float> e : map.entrySet()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("index", e.getKey());
            entry.addProperty("multiplier", e.getValue());
            arr.add(entry);
        }
        return arr;
    }

    private static void jsonToMultiplierArray(JsonArray arr, Map<Integer, Float> out) {
        for (JsonElement elem : arr) {
            JsonObject entry = elem.getAsJsonObject();
            out.put(
                entry.get("index")
                    .getAsInt(),
                entry.get("multiplier")
                    .getAsFloat());
        }
    }

    // ── Recipe property helpers ──

    @SuppressWarnings("unchecked")
    private static <T> void serializeProperty(JsonObject obj, RecipeProperty<?> prop, Object value) {
        ((RecipeProperty<T>) prop).serialize(obj, (T) value);
    }

    @SuppressWarnings("unchecked")
    private static <T> void setProperty(ExtractedProperties props, RecipeProperty<?> prop, Object value) {
        props.set((RecipeProperty<T>) prop, (T) value);
    }

    // ── Mermaid helpers ──

    private static String mermaidId(UUID uuid) {
        return "n" + uuid.toString()
            .replace("-", "")
            .substring(0, 8);
    }

    private static String escapeMermaid(String s) {
        return s.replace("\"", "#quot;")
            .replace("\n", "<br/>");
    }

    @SuppressWarnings("unchecked")
    private static String edgeLabel(Graph graph, Edge edge) {
        Node src = graph.nodes.get(edge.sourceNodeId);
        if (src == null) return "";

        int idx = edge.sourceOutputIndex;
        if (idx < src.outputs.size()) {
            ItemStack stack = src.outputs.get(idx)
                .left();
            if (stack != null) return stack.getDisplayName();
        }
        int fluidIdx = idx - src.outputs.size();
        if (fluidIdx >= 0 && fluidIdx < src.fluidOutputs.size()) {
            FluidStack fs = src.fluidOutputs.get(fluidIdx)
                .left();
            if (fs != null && fs.getFluid() != null) return fs.getLocalizedName();
        }
        return "";
    }
}
