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

import javax.annotation.Nonnull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipeResource;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.Summary.SummaryMode;

import codechicken.nei.recipe.Recipe;

public final class Serializer {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .enableComplexMapKeySerialization()
        .registerTypeAdapter(GraphData.class, new GraphDataAdapter())
        .create();

    // ── Public API ──

    /**
     * Encodes a full graph to a compressed base64 string (gzip + json + base64).
     */
    @Nonnull
    public static String encode(final Graph graph) {
        try {
            final String json = GSON.toJson(graphToJson(graph));
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (final GZIPOutputStream gzip = new GZIPOutputStream(baos);
                final OutputStreamWriter writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            return Base64.getEncoder()
                .encodeToString(baos.toByteArray());
        } catch (final Exception e) {
            throw new RuntimeException("Failed to encode flowchart", e);
        }
    }

    /**
     * Decodes a compressed base64 string back to a Graph.
     */
    @Nonnull
    public static Graph decode(final String data) {
        try {
            final byte[] bytes = Base64.getDecoder()
                .decode(data);
            final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            final StringBuilder json = new StringBuilder();
            try (final GZIPInputStream gzip = new GZIPInputStream(bais);
                final InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
                final char[] buf = new char[4096];
                int len;
                while ((len = reader.read(buf)) != -1) {
                    json.append(buf, 0, len);
                }
            }
            final JsonObject root = GSON.fromJson(json.toString(), JsonObject.class);
            return jsonToGraph(root);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to decode flowchart", e);
        }
    }

    // ── SlotSet serialization ──

    /**
     * Encodes a SlotSet (with all its graphs) to a JSON string.
     */
    @Nonnull
    public static String encode(final SlotSet set) {
        final JsonObject root = new JsonObject();
        root.addProperty("active", set.activeSlot);
        root.addProperty("summaryX", set.summaryX);
        root.addProperty("summaryY", set.summaryY);
        root.addProperty("summaryCollapsed", set.summaryCollapsed);
        root.addProperty("summaryMode", set.summaryMode.name());
        final JsonArray arr = new JsonArray();
        for (final SlotSet.Slot slot : set.slots) {
            final JsonObject slotObj = new JsonObject();
            slotObj.addProperty("name", slot.name);
            slotObj.addProperty("data", encode(slot.graph));
            arr.add(slotObj);
        }
        root.add("slots", arr);
        return GSON.toJson(root);
    }

    /**
     * Decodes a SlotSet (with all its graphs) from a JSON string.
     */
    @Nonnull
    public static SlotSet decodeSlotSet(final String json) {
        final JsonObject root = GSON.fromJson(json, JsonObject.class);
        final SlotSet set = new SlotSet();
        set.activeSlot = root.get("active")
            .getAsInt();
        set.summaryX = root.has("summaryX") ? root.get("summaryX")
            .getAsInt() : SlotSet.DEFAULT_SUMMARY_X;
        set.summaryY = root.has("summaryY") ? root.get("summaryY")
            .getAsInt() : SlotSet.DEFAULT_SUMMARY_Y;
        set.summaryCollapsed = root.has("summaryCollapsed") && root.get("summaryCollapsed")
            .getAsBoolean();
        if (root.has("summaryMode")) {
            try {
                set.summaryMode = SummaryMode.valueOf(
                    root.get("summaryMode")
                        .getAsString());
            } catch (final IllegalArgumentException ignored) {}
        }
        final JsonArray arr = root.getAsJsonArray("slots");
        for (final JsonElement elem : arr) {
            final JsonObject obj = elem.getAsJsonObject();
            final String name = obj.get("name")
                .getAsString();
            final String data = obj.get("data")
                .getAsString();
            final Graph graph = decode(data);
            set.slots.add(new SlotSet.Slot(name, graph));
        }
        return set;
    }

    /**
     * Renders a graph as a Mermaid.js flowchart (LR layout).
     */
    @Nonnull
    public static String toMermaid(final Graph graph) {
        final StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");

        // Map UUIDs to short mermaid-safe IDs
        for (final Node node : graph.getNodes()) {
            final String id = mermaidId(node.id);
            final String label = node.machineName.isEmpty() ? "?" : node.machineName;
            sb.append("    ")
                .append(id)
                .append("[\"")
                .append(escapeMermaid(label))
                .append("\"]\n");
        }

        for (final Edge edge : graph.getEdges()) {
            final String srcId = mermaidId(edge.sourceNodeId);
            final String dstId = mermaidId(edge.targetNodeId);
            final String label = edgeLabel(graph, edge);
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

        for (final Note note : graph.notes.values()) {
            sb.append("    %% Note: ");
            for (String s : note.getText()) sb.append(escapeMermaid(s))
                .append("\n");
        }

        return sb.toString();
    }

    @Nonnull
    private static JsonObject graphToJson(final Graph graph) {
        final JsonObject root = new JsonObject();

        root.addProperty(
            "balanceMode",
            graph.getBalanceMode()
                .name());
        root.addProperty("zoom", graph.getZoom());
        root.addProperty("panX", graph.getPanX());
        root.addProperty("panY", graph.getPanY());

        final JsonArray nodesArray = new JsonArray();
        for (final Node node : graph.getNodes()) {
            final JsonObject obj = new JsonObject();
            obj.addProperty("id", node.id.toString());
            obj.addProperty("x", node.x);
            obj.addProperty("y", node.y);
            obj.addProperty("machine", node.machineName);
            obj.addProperty("ticks", node.durationTicks);
            obj.add("recipeId", node.recipeId.toJsonObject());
            obj.addProperty("handlerRecipeIndex", node.handlerRecipeIndex);
            obj.addProperty("extractorIndex", node.getExtractorIndex());
            obj.add("inputs", portListToJson(node.inputs));
            obj.add("outputs", portListToJson(node.outputs));

            if (node.machineConfig.hasAnyBoost()) {
                obj.add("machineConfig", machineConfigToJson(node.machineConfig));
            }

            if (!node.properties.isEmpty()) {
                final JsonArray propsObj = new JsonArray();
                for (final Map.Entry<RecipeProperty<?>, Object> entry : node.properties.entrySet()) {
                    final JsonObject e = new JsonObject();
                    e.addProperty(
                        "key",
                        entry.getKey()
                            .getKey());
                    entry.getKey()
                        .serialize(e, entry.getValue());
                    propsObj.add(e);
                }
                obj.add("properties", propsObj);
            }

            nodesArray.add(obj);
        }
        root.add("nodes", nodesArray);

        final JsonArray edgesArray = new JsonArray();
        for (final Edge edge : graph.getEdges()) {
            final JsonObject obj = new JsonObject();
            obj.addProperty("id", edge.id.toString());
            obj.addProperty("src", edge.sourceNodeId.toString());
            obj.addProperty("dst", edge.targetNodeId.toString());
            obj.addProperty("srcOut", edge.sourceOutputIndex);
            obj.addProperty("dstIn", edge.targetInputIndex);
            edgesArray.add(obj);
        }
        root.add("edges", edgesArray);

        root.add("notes", GSON.toJsonTree(graph.getNotes()));

        root.add("groups", GSON.toJsonTree(graph.getGroups()));

        return root;
    }

    @Nonnull
    private static Graph jsonToGraph(final JsonObject root) {
        final Graph graph = new Graph();

        if (root.has("balanceMode")) {
            try {
                graph.setBalanceMode(
                    BalanceMode.valueOf(
                        root.get("balanceMode")
                            .getAsString()));
            } catch (final IllegalArgumentException ignored) {}
        }
        graph.setZoom(
            root.get("zoom")
                .getAsFloat());
        graph.setPanX(
            root.get("panX")
                .getAsFloat());
        graph.setPanY(
            root.get("panY")
                .getAsFloat());

        final JsonArray nodesArray = root.getAsJsonArray("nodes");
        for (final JsonElement elem : nodesArray) {
            final JsonObject obj = elem.getAsJsonObject();
            final UUID id = UUID.fromString(
                obj.get("id")
                    .getAsString());
            final int x = obj.get("x")
                .getAsInt();
            final int y = obj.get("y")
                .getAsInt();
            final Node node = new Node(id, x, y);
            node.machineName = obj.get("machine")
                .getAsString();
            node.durationTicks = obj.get("ticks")
                .getAsInt();
            node.recipeId = Recipe.RecipeId.of(
                obj.get("recipeId")
                    .getAsJsonObject());
            node.handlerRecipeIndex = obj.has("handlerRecipeIndex") ? obj.get("handlerRecipeIndex")
                .getAsInt() : 0;
            node.setExtractorIndex(
                obj.has("extractorIndex") ? obj.get("extractorIndex")
                    .getAsInt() : 0);
            node.initExtractor();
            jsonArrayToPorts(obj.getAsJsonArray("inputs"), node.inputs);
            jsonArrayToPorts(obj.getAsJsonArray("outputs"), node.outputs);

            if (obj.has("machineConfig")) {
                jsonToMachineConfig(obj.getAsJsonObject("machineConfig"), node.machineConfig);
            }

            if (obj.has("properties")) {
                final JsonArray propsObj = obj.getAsJsonArray("properties");
                for (int i = 0; i < propsObj.size(); i++) {
                    final JsonObject o = propsObj.get(i)
                        .getAsJsonObject();
                    final RecipeProperty<?> prop = RecipePropertyAPI.getProperty(
                        o.get("key")
                            .getAsString());
                    assert prop != null;

                    final Object value = prop.deserialize(o);
                    if (value != null && !value.equals(prop.getDefaultValue())) {
                        node.properties.put(prop, value);
                    }
                }
            }

            graph.addNode(node);
        }

        final JsonArray edgesArray = root.getAsJsonArray("edges");
        for (final JsonElement elem : edgesArray) {
            final JsonObject obj = elem.getAsJsonObject();
            final UUID id = UUID.fromString(
                obj.get("id")
                    .getAsString());
            final UUID src = UUID.fromString(
                obj.get("src")
                    .getAsString());
            final UUID dst = UUID.fromString(
                obj.get("dst")
                    .getAsString());
            final int srcOut = obj.get("srcOut")
                .getAsInt();
            final int dstIn = obj.get("dstIn")
                .getAsInt();
            graph.addEdge(new Edge(id, src, dst, srcOut, dstIn));
        }

        final JsonArray notesArray = root.getAsJsonArray("notes");
        for (final JsonElement elem : notesArray) {
            final Note note = GSON.fromJson(elem, Note.class);
            graph.notes.put(note.getId(), note);
        }

        final JsonArray groupsArray = root.getAsJsonArray("groups");
        for (final JsonElement elem : groupsArray) {
            final Group group = GSON.fromJson(elem, Group.class);
            graph.groups.put(group.getId(), group);
        }

        return graph;
    }

    // ── Port helpers ──

    @Nonnull
    private static JsonArray portListToJson(final List<Port<?>> ports) {
        final JsonArray arr = new JsonArray();
        for (final Port<?> port : ports) {
            final JsonObject obj = new JsonObject();
            obj.addProperty(
                "type",
                port.getType()
                    .getKey());
            port.getType()
                .serialize(obj, port.getValue());
            obj.addProperty("chance", port.getChance());
            arr.add(obj);
        }
        return arr;
    }

    @SuppressWarnings("unchecked")
    private static void jsonArrayToPorts(final JsonArray arr, final List<Port<?>> out) {
        for (final JsonElement elem : arr) {
            final JsonObject obj = elem.getAsJsonObject();
            final String key = obj.has("type") ? obj.get("type")
                .getAsString() : "item";
            final RecipeResource<?> res = (RecipeResource<?>) RecipePropertyAPI.getProperty(key);
            if (res == null) continue;
            final Object value = res.deserialize(obj);
            if (value == null) continue;
            final float chance = obj.get("chance")
                .getAsFloat();
            final RecipeResource<Object> resource = (RecipeResource<Object>) res;
            out.add(new Port<>(resource, value, chance));
        }
    }

    // ── Machine config ──

    @Nonnull
    private static JsonObject machineConfigToJson(final MachineConfig cfg) {
        final JsonObject obj = new JsonObject();
        final MachineProfile profile = cfg.getProfile();

        if (!MachineProfileRegistry.defaultId()
            .equals(cfg.profileId)) {
            obj.addProperty("profile", cfg.profileId);
        }

        final JsonObject settingsObj = new JsonObject();
        for (final SettingDef<?> def : profile.settings()) {
            final Object val = cfg.settings.get(def.key);
            if (val == null) continue;
            if (val.equals(def.defaultValue)) continue;
            if (val instanceof final Boolean b) settingsObj.addProperty(def.key, b);
            else if (val instanceof final Integer i) settingsObj.addProperty(def.key, i);
            else if (val instanceof final String s) settingsObj.addProperty(def.key, s);
        }
        if (!settingsObj.entrySet()
            .isEmpty()) obj.add("settings", settingsObj);

        if (!cfg.inputConsumption.isEmpty()) {
            obj.add("inMul", multiplierArrayToJson(cfg.inputConsumption));
        }
        if (!cfg.outputProductivity.isEmpty()) {
            obj.add("outMul", multiplierArrayToJson(cfg.outputProductivity));
        }

        return obj;
    }

    private static void jsonToMachineConfig(final JsonObject obj, final MachineConfig cfg) {
        if (obj.has("profile")) {
            cfg.profileId = obj.get("profile")
                .getAsString();
            if (obj.has("settings")) {
                final JsonObject settingsObj = obj.getAsJsonObject("settings");
                for (final Map.Entry<String, JsonElement> entry : settingsObj.entrySet()) {
                    final JsonElement el = entry.getValue();
                    if (el.isJsonPrimitive()) {
                        final var prim = el.getAsJsonPrimitive();
                        if (prim.isBoolean()) cfg.settings.put(entry.getKey(), prim.getAsBoolean());
                        else if (prim.isNumber()) cfg.settings.put(entry.getKey(), prim.getAsInt());
                        else cfg.settings.put(entry.getKey(), prim.getAsString());
                    }
                }
            }
        }

        if (obj.has("inMul")) {
            jsonToMultiplierArray(obj.getAsJsonArray("inMul"), cfg.inputConsumption);
        }
        if (obj.has("outMul")) {
            jsonToMultiplierArray(obj.getAsJsonArray("outMul"), cfg.outputProductivity);
        }
    }

    // ── Multiplier helpers ──

    @Nonnull
    private static JsonArray multiplierArrayToJson(final Map<Integer, Float> map) {
        final JsonArray arr = new JsonArray();
        for (final Map.Entry<Integer, Float> e : map.entrySet()) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("index", e.getKey());
            entry.addProperty("multiplier", e.getValue());
            arr.add(entry);
        }
        return arr;
    }

    private static void jsonToMultiplierArray(final JsonArray arr, final Map<Integer, Float> out) {
        for (final JsonElement elem : arr) {
            final JsonObject entry = elem.getAsJsonObject();
            out.put(
                entry.get("index")
                    .getAsInt(),
                entry.get("multiplier")
                    .getAsFloat());
        }
    }

    // ── Mermaid helpers ──

    @Nonnull
    private static String mermaidId(final UUID uuid) {
        return "n" + uuid.toString()
            .replace("-", "")
            .substring(0, 8);
    }

    @Nonnull
    private static String escapeMermaid(final String s) {
        return s.replace("\"", "#quot;")
            .replace("\n", "<br/>");
    }

    @Nonnull
    private static String edgeLabel(final Graph graph, final Edge edge) {
        final Node src = graph.nodes.get(edge.sourceNodeId);
        if (src == null) return "";

        final int idx = edge.sourceOutputIndex;
        if (idx >= 0 && idx < src.outputs.size()) {
            final Port port = src.outputs.get(idx);
            return port.getDisplayName();
        }
        return "";
    }
}
