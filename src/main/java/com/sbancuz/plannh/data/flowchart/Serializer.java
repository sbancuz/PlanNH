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
import com.sbancuz.plannh.data.flowchart.Balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.Summary.SummaryMode;

import codechicken.nei.recipe.Recipe;
import it.unimi.dsi.fastutil.objects.ObjectFloatImmutablePair;

public final class Serializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    // ── Public API ──

    /** Encodes a full graph to a compressed base64 string (gzip + json + base64). */
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

    /** Decodes a compressed base64 string back to a Graph. */
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

    /** Encodes a SlotSet (with all its graphs) to a JSON string. */
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

    /** Decodes a SlotSet (with all its graphs) from a JSON string. */
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

    /** Renders a graph as a Mermaid.js flowchart (LR layout). */
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
            sb.append("    %% Note: ")
                .append(escapeMermaid(note.text))
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
            obj.add("inputs", itemStackArrayToJson(node.inputs));
            obj.add("outputs", itemStackArrayToJson(node.outputs));
            obj.add("fluidInputs", fluidStackArrayToJson(node.fluidInputs));
            obj.add("fluidOutputs", fluidStackArrayToJson(node.fluidOutputs));

            if (node.machineConfig.hasAnyBoost()) {
                obj.add("machineConfig", machineConfigToJson(node.machineConfig));
            }

            if (!node.properties.isEmpty()) {
                final JsonObject propsObj = new JsonObject();
                for (final Map.Entry<RecipeProperty<?>, Object> entry : node.properties.entrySet()) {
                    serializeProperty(propsObj, entry.getKey(), entry.getValue());
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

        final JsonArray notesArray = new JsonArray();
        for (final Note note : graph.notes.values()) {
            final JsonObject obj = new JsonObject();
            obj.addProperty("id", note.id.toString());
            obj.addProperty("x", note.x);
            obj.addProperty("y", note.y);
            obj.addProperty("text", note.text);
            notesArray.add(obj);
        }
        root.add("notes", notesArray);

        final JsonArray groupsArray = new JsonArray();
        for (final Group group : graph.getGroups()) {
            final JsonObject obj = new JsonObject();
            obj.addProperty("id", group.id.toString());
            obj.addProperty("title", group.title);
            obj.addProperty("x", group.x);
            obj.addProperty("y", group.y);
            obj.addProperty("width", group.width);
            obj.addProperty("height", group.height);
            obj.addProperty("collapsed", group.collapsed);
            if (group.colorOverride != 0) obj.addProperty("colorOverride", group.colorOverride);
            if (group.clampNodes) obj.addProperty("clampNodes", true);
            if (group.autoResize) obj.addProperty("autoResize", true);
            final JsonArray nodeIdsArray = new JsonArray();
            for (final UUID nid : group.nodeIds) {
                nodeIdsArray.add(new com.google.gson.JsonPrimitive(nid.toString()));
            }
            obj.add("nodeIds", nodeIdsArray);
            groupsArray.add(obj);
        }
        root.add("groups", groupsArray);

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
            jsonArrayToItemStacks(obj.getAsJsonArray("inputs"), node.inputs);
            jsonArrayToItemStacks(obj.getAsJsonArray("outputs"), node.outputs);
            if (obj.has("fluidInputs")) jsonArrayToFluidStacks(obj.getAsJsonArray("fluidInputs"), node.fluidInputs);
            if (obj.has("fluidOutputs")) jsonArrayToFluidStacks(obj.getAsJsonArray("fluidOutputs"), node.fluidOutputs);

            if (obj.has("machineConfig")) {
                jsonToMachineConfig(obj.getAsJsonObject("machineConfig"), node.machineConfig);
            }

            if (obj.has("properties")) {
                final JsonObject propsObj = obj.getAsJsonObject("properties");
                for (final RecipeProperty<?> prop : RecipePropertyAPI.getProperties()) {
                    final Object value = prop.deserialize(propsObj);
                    if (value != null && !value.equals(prop.getDefaultValue())) {
                        setProperty(node.properties, prop, value);
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

        if (root.has("notes")) {
            final JsonArray notesArray = root.getAsJsonArray("notes");
            for (final JsonElement elem : notesArray) {
                final JsonObject obj = elem.getAsJsonObject();
                final UUID id = UUID.fromString(
                    obj.get("id")
                        .getAsString());
                final int x = obj.get("x")
                    .getAsInt();
                final int y = obj.get("y")
                    .getAsInt();
                final Note note = new Note(id, x, y);
                if (obj.has("text")) note.text = obj.get("text")
                    .getAsString();
                graph.notes.put(id, note);
            }
        }

        if (root.has("groups")) {
            final JsonArray groupsArray = root.getAsJsonArray("groups");
            for (final JsonElement elem : groupsArray) {
                final JsonObject obj = elem.getAsJsonObject();
                final UUID id = UUID.fromString(
                    obj.get("id")
                        .getAsString());
                final String title = obj.get("title")
                    .getAsString();
                final int x = obj.get("x")
                    .getAsInt();
                final int y = obj.get("y")
                    .getAsInt();
                final int w = obj.get("width")
                    .getAsInt();
                final int h = obj.get("height")
                    .getAsInt();
                final boolean collapsed = obj.has("collapsed") && obj.get("collapsed")
                    .getAsBoolean();
                final Group group = new Group(id, x, y, w, h, title);
                group.collapsed = collapsed;
                if (obj.has("colorOverride")) group.colorOverride = obj.get("colorOverride")
                    .getAsInt();
                if (obj.has("clampNodes")) group.clampNodes = obj.get("clampNodes")
                    .getAsBoolean();
                if (obj.has("autoResize")) group.autoResize = obj.get("autoResize")
                    .getAsBoolean();
                if (obj.has("nodeIds")) {
                    final JsonArray nodeIdsArray = obj.getAsJsonArray("nodeIds");
                    for (final JsonElement nidElem : nodeIdsArray) {
                        group.nodeIds.add(UUID.fromString(nidElem.getAsString()));
                    }
                }
                graph.addGroup(group);
            }
        }

        return graph;
    }

    // ── Item / Fluid stack helpers ──

    @Nonnull
    private static JsonArray itemStackArrayToJson(final List<ObjectFloatImmutablePair<ItemStack>> stacks) {
        final JsonArray arr = new JsonArray();
        for (final var pair : stacks) {
            if (pair.left() == null) continue;
            final ItemStack stack = pair.left();
            final JsonObject obj = new JsonObject();
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

    private static void jsonArrayToItemStacks(final JsonArray arr,
        final List<ObjectFloatImmutablePair<ItemStack>> out) {
        for (final JsonElement elem : arr) {
            final JsonObject obj = elem.getAsJsonObject();
            final String itemId = obj.get("id")
                .getAsString();
            final int size = obj.get("size")
                .getAsInt();
            final int meta = obj.get("meta")
                .getAsInt();
            final ItemStack stack = new ItemStack(
                (net.minecraft.item.Item) net.minecraft.item.Item.itemRegistry.getObject(itemId),
                size,
                meta);
            if (obj.has("nbt")) {
                try {
                    stack.setTagCompound(
                        (net.minecraft.nbt.NBTTagCompound) net.minecraft.nbt.JsonToNBT.func_150315_a(
                            obj.get("nbt")
                                .getAsString()));
                } catch (final net.minecraft.nbt.NBTException ignored) {}
            }
            out.add(
                new ObjectFloatImmutablePair<>(
                    stack,
                    obj.get("chance")
                        .getAsFloat()));
        }
    }

    @Nonnull
    private static JsonArray fluidStackArrayToJson(final List<ObjectFloatImmutablePair<FluidStack>> fluids) {
        final JsonArray arr = new JsonArray();
        for (final var pair : fluids) {
            final FluidStack fs = pair.left();
            if (fs == null || fs.getFluid() == null) continue;
            final JsonObject obj = new JsonObject();
            obj.addProperty("fluid", FluidRegistry.getFluidName(fs.getFluid()));
            obj.addProperty("amount", fs.amount);
            obj.addProperty("chance", pair.rightFloat());
            arr.add(obj);
        }
        return arr;
    }

    private static void jsonArrayToFluidStacks(final JsonArray arr,
        final List<ObjectFloatImmutablePair<FluidStack>> out) {
        for (final JsonElement elem : arr) {
            final JsonObject obj = elem.getAsJsonObject();
            final String name = obj.get("fluid")
                .getAsString();
            final int amount = obj.get("amount")
                .getAsInt();
            final FluidStack fs = FluidRegistry.getFluidStack(name, amount);
            if (fs != null) out.add(
                new ObjectFloatImmutablePair<>(
                    fs,
                    obj.get("chance")
                        .getAsFloat()));
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

        if (profile != null) {
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
        }

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

        cfg.initDefaults();

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

    // ── Recipe property helpers ──

    @SuppressWarnings("unchecked")
    private static <T> void serializeProperty(final JsonObject obj, final RecipeProperty<?> prop, final Object value) {
        ((RecipeProperty<T>) prop).serialize(obj, (T) value);
    }

    @SuppressWarnings("unchecked")
    private static <T> void setProperty(final ExtractedProperties props, final RecipeProperty<?> prop,
        final Object value) {
        props.set((RecipeProperty<T>) prop, (T) value);
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
        if (idx < src.outputs.size()) {
            final ItemStack stack = src.outputs.get(idx)
                .left();
            if (stack != null) return stack.getDisplayName();
        }
        final int fluidIdx = idx - src.outputs.size();
        if (fluidIdx >= 0 && fluidIdx < src.fluidOutputs.size()) {
            final FluidStack fs = src.fluidOutputs.get(fluidIdx)
                .left();
            if (fs != null && fs.getFluid() != null) return fs.getLocalizedName();
        }
        return "";
    }
}
