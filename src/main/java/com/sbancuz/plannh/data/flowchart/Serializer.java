package com.sbancuz.plannh.data.flowchart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nonnull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.flowchart.Summary.SummarySection;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.balancer.ChoiceKey;
import com.sbancuz.plannh.data.flowchart.balancer.PortRef;

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

    // ── Plan serialization ──

    /**
     * Encodes a Plan (with all its graphs) to a JSON string. Graph bodies are stored compressed,
     * each with its slot name and summary section folds.
     */
    @Nonnull
    public static String encodePlan(final Plan plan) {
        final JsonObject root = GSON.toJsonTree(plan, Plan.class)
            .getAsJsonObject();

        final JsonArray arr = new JsonArray();
        for (final Graph graph : plan.getGraphs()) {
            final JsonObject slotObj = new JsonObject();
            slotObj.addProperty("name", graph.getName());
            slotObj.addProperty("data", encode(graph));
            slotObj.add("sectionFolds", foldsToJson(graph.collapsedSummarySections));
            arr.add(slotObj);
        }
        root.add("graphs", arr);

        return GSON.toJson(root);
    }

    /**
     * Encodes a Plan (with all its graphs) to a JSON string. Graph bodies are stored compressed,
     * each with its slot name and summary section folds.
     */
    @Nonnull
    public static String encodePlanDebug(final Plan plan) {
        final JsonObject root = GSON.toJsonTree(plan, Plan.class)
            .getAsJsonObject();

        final JsonArray arr = new JsonArray();
        for (final Graph graph : plan.getGraphs()) {
            final JsonObject slotObj = new JsonObject();
            slotObj.addProperty("name", graph.getName());
            slotObj.add("data", graphToJson(graph));
            slotObj.add("sectionFolds", foldsToJson(graph.collapsedSummarySections));
            arr.add(slotObj);
        }
        root.add("graphs", arr);

        return GSON.toJson(root);
    }

    /**
     * Decodes a Plan (with all its graphs) from a JSON string.
     */
    @Nonnull
    public static Plan decodePlan(final String json) {
        final Plan plan = GSON.fromJson(json, Plan.class);

        // graphs need to be decoded
        final JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root.has("graphs")) {
            for (final JsonElement elem : root.getAsJsonArray("graphs")) {
                final JsonObject obj = elem.getAsJsonObject();
                final String name = obj.has("name") ? obj.get("name")
                    .getAsString() : "";
                // One unreadable chart costs that chart, not the save; the empty graph keeps slot
                // numbering in place.
                Graph graph;
                try {
                    graph = decode(
                        obj.get("data")
                            .getAsString());
                } catch (final RuntimeException e) {
                    PlanNH.LOG.error("Slot '{}' could not be read and was left empty", name, e);
                    graph = new Graph(name);
                }
                graph.setName(name);
                if (obj.has("sectionFolds")) {
                    foldsFromJson(obj.getAsJsonObject("sectionFolds"), graph.collapsedSummarySections);
                }
                plan.getGraphs()
                    .add(graph);
            }
        }

        return plan;
    }

    /**
     * Every section, not just the folded ones: a section this save has never heard of has to be
     * distinguishable from one the user deliberately left open, or adding a section would silently
     * unfold it for everyone who had already saved.
     */
    private static JsonObject foldsToJson(final Set<SummarySection> folded) {
        final JsonObject folds = new JsonObject();
        for (final SummarySection section : SummarySection.values()) {
            folds.addProperty(section.name(), folded.contains(section));
        }
        return folds;
    }

    /**
     * Reads section by section over whatever {@code folded} already holds rather than replacing it:
     * an unmentioned section is one the save predates, and it keeps the fold a fresh chart gives it.
     */
    private static void foldsFromJson(final JsonObject folds, final Set<SummarySection> folded) {
        for (final var fold : folds.entrySet()) {
            final SummarySection section;
            try {
                section = SummarySection.valueOf(fold.getKey());
            } catch (final IllegalArgumentException ignored) {
                continue; // a section this build has dropped
            }
            if (fold.getValue()
                .getAsBoolean()) {
                folded.add(section);
            } else {
                folded.remove(section);
            }
        }
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

        for (final Note note : graph.getNotes()) {
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
        root.addProperty("opsMode", graph.isOpsMode());
        // The chosen answer travels as the ports it opens, never as gate indices: those are rebuilt
        // from scratch on every solve and mean nothing across a save.
        if (graph.getExcessChoice() != null) {
            final JsonArray anchors = new JsonArray();
            for (final PortRef ref : graph.getExcessChoice()
                .gateAnchors()) {
                final JsonObject a = new JsonObject();
                a.addProperty(
                    "node",
                    ref.nodeId()
                        .toString());
                a.addProperty("port", ref.portIndex());
                a.addProperty("input", ref.input());
                anchors.add(a);
            }
            root.add("excessChoice", anchors);
        }
        root.addProperty("zoom", graph.getZoom());
        root.addProperty("panX", graph.getPanX());
        root.addProperty("panY", graph.getPanY());
        root.addProperty("name", graph.getName());

        final JsonArray nodesArray = new JsonArray();
        for (final Node node : graph.getNodes()) {
            final JsonObject obj = new JsonObject();
            obj.addProperty("id", node.id.toString());
            obj.addProperty("x", node.x);
            obj.addProperty("y", node.y);
            obj.addProperty("machine", node.machineName);
            // Null-tolerant on both sides: an unresolved recipe must not make the slot
            // unsaveable, and downstream treats a null recipeId as "handler unavailable".
            if (node.recipeId != null) {
                obj.add("recipeId", node.recipeId.toJsonObject());
            }
            obj.addProperty("handlerRecipeIndex", node.handlerRecipeIndex);
            obj.addProperty("extractorIndex", node.getExtractorIndex());
            obj.addProperty("machineCount", node.machineConfig.getMachineCount());
            if (node.isMachineCountFixed()) {
                obj.addProperty("machineCountFixed", true);
            }
            if (!node.targetOutputRates.isEmpty()) {
                final JsonObject targets = new JsonObject();
                for (final Map.Entry<Integer, Double> t : node.targetOutputRates.entrySet()) {
                    targets.addProperty(String.valueOf(t.getKey()), t.getValue());
                }
                obj.add("targets", targets);
            }

            obj.add("inputs", portListToJson(node.inputs));
            obj.add("outputs", portListToJson(node.outputs));

            if (node.machineConfig.hasStoredSettings()) {
                obj.add("machineConfig", machineConfigToJson(node.machineConfig));
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

        final JsonArray notesArray = new JsonArray();
        for (Note note : graph.getNotes()) notesArray.add(GSON.toJsonTree(note));
        root.add("notes", notesArray);

        final JsonArray groupsArray = new JsonArray();
        for (Group group : graph.getGroups()) groupsArray.add(GSON.toJsonTree(group));
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
        if (root.has("opsMode")) {
            graph.setOpsMode(
                root.get("opsMode")
                    .getAsBoolean());
        }
        // Read independently of everything else, like the per-node targets: an old save has no such
        // key, and a corrupt one costs the user a preference rather than the chart.
        if (root.has("excessChoice")) {
            try {
                final List<PortRef> anchors = new ArrayList<>();
                for (final JsonElement elem : root.getAsJsonArray("excessChoice")) {
                    final JsonObject a = elem.getAsJsonObject();
                    anchors.add(
                        new PortRef(
                            UUID.fromString(
                                a.get("node")
                                    .getAsString()),
                            a.get("port")
                                .getAsInt(),
                            a.get("input")
                                .getAsBoolean()));
                }
                if (!anchors.isEmpty()) graph.setExcessChoice(ChoiceKey.of(anchors));
            } catch (final RuntimeException ignored) {}
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
            if (obj.has("recipeId")) {
                node.recipeId = Recipe.RecipeId.of(
                    obj.get("recipeId")
                        .getAsJsonObject());
            }
            node.handlerRecipeIndex = obj.has("handlerRecipeIndex") ? obj.get("handlerRecipeIndex")
                .getAsInt() : 0;
            node.initExtractor();
            node.refresh();

            if (obj.has("machineCount")) {
                node.machineConfig.setMachineCount(
                    obj.get("machineCount")
                        .getAsInt());
            }
            node.setMachineCountFixed(
                obj.has("machineCountFixed") && obj.get("machineCountFixed")
                    .getAsBoolean());
            // Read independently of every other key.
            if (obj.has("targets")) {
                for (final Map.Entry<String, JsonElement> t : obj.getAsJsonObject("targets")
                    .entrySet()) {
                    try {
                        node.targetOutputRates.put(
                            Integer.parseInt(t.getKey()),
                            t.getValue()
                                .getAsDouble());
                    } catch (final NumberFormatException ignored) {
                        // A malformed key loses one target, not the chart.
                    }
                }
            }

            if (obj.has("inputs")) {
                applySavedPortChances(obj.getAsJsonArray("inputs"), node.inputs);
            }
            if (obj.has("outputs")) {
                applySavedPortChances(obj.getAsJsonArray("outputs"), node.outputs);
            }

            if (obj.has("machineConfig")) {
                jsonToMachineConfig(obj.getAsJsonObject("machineConfig"), node.machineConfig);
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

        for (final JsonElement elem : root.getAsJsonArray("notes")) {
            final Note note = GSON.fromJson(elem, Note.class);
            graph.notes.put(note.getId(), note);
        }

        for (final JsonElement elem : root.getAsJsonArray("groups")) {
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

        if (!MachineProfileRegistry.defaultId()
            .equals(cfg.profileId)) {
            obj.addProperty("profile", cfg.profileId);
        }

        // Walk what the node actually stores, not what its profile declares: the map is sparse, so
        // a key being there is already the statement "the user chose this". Iterating the defs
        // instead used to silently drop any stored key the current profile no longer lists.
        final JsonObject settingsObj = new JsonObject();
        for (final Map.Entry<String, Object> entry : cfg.settings.entrySet()) {
            // The machine count has its own slot and is rewritten by the solver every frame.
            if (Settings.MACHINES.key()
                .equals(entry.getKey())) continue;
            final Object val = entry.getValue();
            if (val instanceof final Boolean b) settingsObj.addProperty(entry.getKey(), b);
            else if (val instanceof final Integer i) settingsObj.addProperty(entry.getKey(), i);
            else if (val instanceof final String s) settingsObj.addProperty(entry.getKey(), s);
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
        // "profile" is omitted for the default profile, but its settings are still written.
        if (obj.has("profile")) {
            cfg.profileId = obj.get("profile")
                .getAsString();
        }
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

        if (obj.has("inMul")) {
            jsonToMultiplierArray(obj.getAsJsonArray("inMul"), cfg.inputConsumption);
        }
        if (obj.has("outMul")) {
            jsonToMultiplierArray(obj.getAsJsonArray("outMul"), cfg.outputProductivity);
        }

        cfg.getProfile()
            .onLoad()
            .accept(cfg.settings);
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
