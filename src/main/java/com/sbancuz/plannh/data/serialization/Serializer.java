package com.sbancuz.plannh.data.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Edge2;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.GraphData;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Note;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;

import codechicken.nei.recipe.Recipe;
import it.unimi.dsi.fastutil.ints.IntIntPair;

public final class Serializer {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .enableComplexMapKeySerialization()
        .registerTypeAdapter(GraphData.class, new GraphDataDeserializer())
        .registerTypeAdapter(Recipe.RecipeId.class, new RecipeIdAdapter())
        .registerTypeAdapter(MachineConfig.class, new MachineConfigAdapter())
        .registerTypeAdapter(IntIntPair.class, new IntIntPairDeserializer())
        .registerTypeAdapter(ItemStack.class, new ItemStackAdapter())
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
            PlanNH.LOG.warn("Failed to decode flowchart: {}", e.getMessage());
            return new Graph("");
        }
    }

    // ── Plan serialization ──

    /**
     * Encodes a Plan (with all its graphs) to a JSON string. Graph bodies are stored compressed,
     * each with its slot name and summary section folds.
     */
    @Nonnull
    public static String encodePlan(final Plan plan) {
        return encodePlan(plan, false);
    }

    /**
     * The same plan, but with each slot's data written as readable JSON instead of the compressed
     * base64 {@code encodePlan} uses - for debugging a save.
     */
    @Nonnull
    public static String encodePlanDebug(final Plan plan) {
        return encodePlan(plan, true);
    }

    /**
     * Encodes a Plan (with all its graphs) to a JSON string. Graph bodies are stored compressed,
     * each with its slot name and summary section folds.
     */
    @Nonnull
    private static String encodePlan(final Plan plan, final boolean debug) {
        final JsonObject root = GSON.toJsonTree(plan, Plan.class)
            .getAsJsonObject();

        // graphs need to be encoded
        final JsonArray graphsArray = new JsonArray();
        for (final Graph graph : plan.getGraphs())
            graphsArray.add(debug ? graphToJson(graph) : new JsonPrimitive(encodeGraph(graph)));
        root.add("graphs", graphsArray);

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

        root.add("balanceMode", GSON.toJsonTree(graph.getBalanceMode()));

        root.addProperty("zoom", graph.getZoom());
        root.addProperty("panX", graph.getPanX());
        root.addProperty("panY", graph.getPanY());
        root.addProperty("name", graph.getName());

        root.add(
            "notes",
            GSON.toJsonTree(
                graph.getNotes()
                    .values()));
        root.add(
            "groups",
            GSON.toJsonTree(
                graph.getGroups()
                    .values()));
        root.add(
            "nodes",
            GSON.toJsonTree(
                graph.getNodes()
                    .values()));
        root.add(
            "edges",
            GSON.toJsonTree(
                graph.getEdges2()
                    .values()));

        return root;
    }

    @Nonnull
    private static Graph jsonToGraph(final JsonObject root) {
        final Graph graph = new Graph(getSafeString(root.get("name"), ""));

        graph.setBalanceMode(getSafe(root.get("balanceMode"), BalanceMode.class, BalanceMode.AUTO));

        graph.setZoom(getSafeFloat(root.get("zoom"), 1));
        graph.setPanX(getSafeFloat(root.get("panX"), 0));
        graph.setPanY(getSafeFloat(root.get("panY"), 0));

        if (root.has("notes")) {
            for (final JsonElement elem : root.getAsJsonArray("notes")) {
                final Note note = GSON.fromJson(elem, Note.class);

                if (note.invalid()) {
                    PlanNH.LOG.warn("Invalid state found for a note!");
                    continue;
                }

                graph.getNotes()
                    .put(note.getId(), note);
            }
        }

        if (root.has("groups")) {
            for (final JsonElement elem : root.getAsJsonArray("groups")) {
                final Group group;
                try {
                    // do this to catch errors in child loading
                    group = GSON.fromJson(elem, Group.class);
                } catch (Exception ignored) {
                    continue;
                }

                if (group.invalid()) {
                    PlanNH.LOG.warn("Invalid state found for a group!");
                    continue;
                }

                graph.getGroups()
                    .put(group.getId(), group);
            }
        }

        if (root.has("nodes")) {
            for (final JsonElement elem : root.getAsJsonArray("nodes")) {
                final Node node = GSON.fromJson(elem, Node.class);

                if (node.invalid()) {
                    PlanNH.LOG.warn("Invalid state found for a node!");
                    continue;
                }

                node.init();
                graph.getNodes()
                    .put(node.getId(), node);
            }
        }

        if (root.has("edges")) {
            for (final JsonElement elem : root.getAsJsonArray("edges")) {
                final Edge2 edge = GSON.fromJson(elem, Edge2.class);

                if (edge.invalid()) {
                    PlanNH.LOG.warn("Invalid state found for an edge!");
                    continue;
                }

                graph.getEdges2()
                    .put(edge.getId(), edge);
            }
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

    private static <T> T getSafe(@Nullable JsonElement json, Type type, T defaultValue) {
        return json == null ? defaultValue : GSON.fromJson(json, type);
    }

    private static <T> T getSafe(JsonElement json, Class<T> clazz, T defaultValue) {
        return getSafe(json, (Type) clazz, defaultValue);
    }

    private static String getSafeString(JsonElement json, String defaultValue) {
        return getSafe(json, String.class, defaultValue);
    }

    private static boolean getSafeBoolean(JsonElement json, boolean defaultValue) {
        return getSafe(json, Boolean.class, defaultValue);
    }

    private static int getSafeInteger(JsonElement json, int defaultValue) {
        return getSafe(json, Integer.class, defaultValue);
    }

    private static float getSafeFloat(JsonElement json, float defaultValue) {
        return getSafe(json, Float.class, defaultValue);
    }
}
