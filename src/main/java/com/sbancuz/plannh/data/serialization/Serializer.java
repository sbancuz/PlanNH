package com.sbancuz.plannh.data.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
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
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.GraphData;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Note;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Port;

import codechicken.nei.recipe.Recipe;

public final class Serializer {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .enableComplexMapKeySerialization()
        .registerTypeAdapter(GraphData.class, new GraphDataDeserializer())
        .registerTypeAdapter(Recipe.RecipeId.class, new RecipeIdAdapter())
        .registerTypeAdapter(MachineConfig.class, new MachineConfigAdapter())
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
            final String id = mermaidId(node.getId());
            final String label = node.getMachineName()
                .isEmpty() ? "?" : node.getMachineName();
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

        final JsonArray nodesArray = new JsonArray();
        for (Node node : graph.getNodes()
            .values()) nodesArray.add(GSON.toJsonTree(node));
        root.add("nodes", nodesArray);

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

        for (final JsonElement elem : root.getAsJsonArray("nodes")) {
            final Node node = GSON.fromJson(elem, Node.class);
            node.init();
            graph.getNodes()
                .put(node.getId(), node);
        }

        return graph;
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
        if (idx >= 0 && idx < src.getOutputs()
            .size()) {
            final Port port = src.getOutputs()
                .get(idx);
            return port.getDisplayName();
        }
        return "";
    }
}
