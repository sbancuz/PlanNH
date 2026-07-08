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
import com.google.gson.JsonPrimitive;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceMode;

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
    public static String encodeGraph(final Graph graph) {
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
    public static Graph decodeGraph(final String data) {
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

    // ── Plan serialization ──

    /**
     * Encodes a Plan (with all its graphs) to a JSON string.
     */
    @Nonnull
    public static String encodePlan(final Plan plan) {
        final JsonObject root = GSON.toJsonTree(plan, Plan.class)
            .getAsJsonObject();

        // graphs need to be encoded
        final JsonArray arr = new JsonArray();
        for (final Graph graph : plan.getGraphs()) arr.add(new JsonPrimitive(encodeGraph(graph)));
        root.add("graphs", arr);

        return GSON.toJson(root);
    }

    @Nonnull
    public static String encodePlanDebug(Plan plan) {
        final JsonObject root = GSON.toJsonTree(plan, Plan.class)
            .getAsJsonObject();

        final JsonArray arr = new JsonArray();
        for (final Graph graph : plan.getGraphs()) arr.add(graphToJson(graph));
        root.add("graphs", arr);

        return GSON.toJson(root);
    }

    /**
     * Decodes a Plan (with all its graphs) from a JSON string.
     */
    @Nonnull
    public static Plan decodePlan(final String json) {
        final Plan plan = GSON.fromJson(json, Plan.class);
        final List<Graph> graphs = plan.getGraphs();

        // graphs need to be decoded
        for (final JsonElement elem : GSON.fromJson(json, JsonObject.class)
            .getAsJsonArray("graphs")) graphs.add(decodeGraph(elem.getAsString()));

        return plan;
    }

    /**
     * Renders a graph as a Mermaid.js flowchart (LR layout).
     */
    @Nonnull
    public static String toMermaid(final Graph graph) {
        final StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");

        // Map UUIDs to short mermaid-safe IDs
        for (final Node node : graph.getNodes()
            .values()) {
            final String id = mermaidId(node.id);
            final String label = node.machineName.isEmpty() ? "?" : node.machineName;
            sb.append("    ")
                .append(id)
                .append("[\"")
                .append(escapeMermaid(label))
                .append("\"]\n");
        }

        for (final Edge edge : graph.getEdges()
            .values()) {
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

        for (final Note note : graph.getNotes()
            .values()) {
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
        root.addProperty("name", graph.getName());

        final JsonArray nodesArray = new JsonArray();
        for (final Node node : graph.getNodes()
            .values()) {
            final JsonObject obj = new JsonObject();
            obj.addProperty("id", node.id.toString());
            obj.addProperty("x", node.x);
            obj.addProperty("y", node.y);
            obj.addProperty("machine", node.machineName);
            obj.add("recipeId", node.recipeId.toJsonObject());
            obj.addProperty("handlerRecipeIndex", node.handlerRecipeIndex);
            obj.addProperty("extractorIndex", node.getExtractorIndex());

            obj.add("inputs", portListToJson(node.inputs));
            obj.add("outputs", portListToJson(node.outputs));

            if (node.machineConfig.hasAnyBoost()) {
                obj.add("machineConfig", machineConfigToJson(node.machineConfig));
            }

            nodesArray.add(obj);
        }
        root.add("nodes", nodesArray);

        final JsonArray edgesArray = new JsonArray();
        for (final Edge edge : graph.getEdges()
            .values()) {
            final JsonObject obj = new JsonObject();
            obj.addProperty("id", edge.id.toString());
            obj.addProperty("src", edge.sourceNodeId.toString());
            obj.addProperty("dst", edge.targetNodeId.toString());
            obj.addProperty("srcOut", edge.sourceOutputIndex);
            obj.addProperty("dstIn", edge.targetInputIndex);
            edgesArray.add(obj);
        }
        root.add("edges", edgesArray);

        final JsonArray notesArray = new JsonArray();
        for (Note note : graph.getNotes()
            .values()) notesArray.add(GSON.toJsonTree(note));
        root.add("notes", notesArray);

        final JsonArray groupsArray = new JsonArray();
        for (Group group : graph.getGroups()
            .values()) groupsArray.add(GSON.toJsonTree(group));
        root.add("groups", groupsArray);

        return root;
    }

    @Nonnull
    private static Graph jsonToGraph(final JsonObject root) {
        final Graph graph = new Graph(
            root.get("name")
                .getAsString());

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
            node.recipeId = Recipe.RecipeId.of(
                obj.get("recipeId")
                    .getAsJsonObject());
            node.handlerRecipeIndex = obj.has("handlerRecipeIndex") ? obj.get("handlerRecipeIndex")
                .getAsInt() : 0;
            node.setExtractorIndex(
                obj.has("extractorIndex") ? obj.get("extractorIndex")
                    .getAsInt() : 0);
            node.initExtractor();
            node.refresh();

            if (obj.has("inputs")) {
                applySavedPortChances(obj.getAsJsonArray("inputs"), node.inputs);
            }
            if (obj.has("outputs")) {
                applySavedPortChances(obj.getAsJsonArray("outputs"), node.outputs);
            }

            if (obj.has("machineConfig")) {
                jsonToMachineConfig(obj.getAsJsonObject("machineConfig"), node.machineConfig);
            }

            graph.getNodes()
                .put(node.id, node);
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
            graph.getEdges()
                .put(id, new Edge(id, src, dst, srcOut, dstIn));
        }

        for (final JsonElement elem : root.getAsJsonArray("notes")) {
            final Note note = GSON.fromJson(elem, Note.class);
            graph.getNotes()
                .put(note.getId(), note);
        }

        for (final JsonElement elem : root.getAsJsonArray("groups")) {
            final Group group = GSON.fromJson(elem, Group.class);
            graph.getGroups()
                .put(group.getId(), group);
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
            obj.addProperty("chance", port.getChance());
            arr.add(obj);
        }
        return arr;
    }

    private static void applySavedPortChances(final JsonArray arr, final List<Port<?>> ports) {
        for (int i = 0; i < arr.size() && i < ports.size(); i++) {
            final JsonObject obj = arr.get(i)
                .getAsJsonObject();
            final String savedType = obj.has("type") ? obj.get("type")
                .getAsString() : "item";
            if (savedType.equals(
                ports.get(i)
                    .getType()
                    .getKey())) {
                ports.get(i)
                    .setChance(
                        obj.get("chance")
                            .getAsFloat());
            }
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
        final Node src = graph.getNodes()
            .get(edge.sourceNodeId);
        if (src == null) return "";

        final int idx = edge.sourceOutputIndex;
        if (idx >= 0 && idx < src.outputs.size()) {
            final Port port = src.outputs.get(idx);
            return port.getDisplayName();
        }
        return "";
    }
}
