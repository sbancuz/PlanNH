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
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sbancuz.plannh.api.RecipePropertyAPI;

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
                obj.addProperty("recipeOwner", node.recipeOwner);
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
                    for (java.util.Map.Entry<RecipeProperty<?>, Object> entry : node.properties.entrySet()) {
                        serializeProperty(propsObj, entry.getKey(), entry.getValue());
                    }
                    obj.add("properties", propsObj);
                }

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
                node.recipeOwner = obj.has("recipeOwner") ? obj.get("recipeOwner")
                    .getAsString() : "";
                node.handlerRecipeIndex = obj.has("handlerRecipeIndex") ? obj.get("handlerRecipeIndex")
                    .getAsInt() : 0;
                jsonArrayToItemStacks(obj.getAsJsonArray("inputs"), node.inputs);
                jsonArrayToItemStacks(obj.getAsJsonArray("outputs"), node.outputs);
                if (obj.has("fluidInputs")) jsonArrayToFluidStacks(obj.getAsJsonArray("fluidInputs"), node.fluidInputs);
                if (obj.has("fluidOutputs"))
                    jsonArrayToFluidStacks(obj.getAsJsonArray("fluidOutputs"), node.fluidOutputs);

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
                graph.addEdge(new FlowchartEdge(id, src, dst, srcOut, dstIn));
            }

            return graph;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize flowchart", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void serializeProperty(JsonObject obj, RecipeProperty<?> prop, Object value) {
        ((RecipeProperty<T>) prop).serialize(obj, (T) value);
    }

    @SuppressWarnings("unchecked")
    private static <T> void setProperty(ExtractedProperties props, RecipeProperty<?> prop, Object value) {
        props.set((RecipeProperty<T>) prop, (T) value);
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

    private static JsonArray fluidStackArrayToJson(java.util.List<FluidStack> fluids) {
        JsonArray arr = new JsonArray();
        for (FluidStack fs : fluids) {
            if (fs == null || fs.getFluid() == null) continue;
            JsonObject obj = new JsonObject();
            obj.addProperty("fluid", FluidRegistry.getFluidName(fs.getFluid()));
            obj.addProperty("amount", fs.amount);
            arr.add(obj);
        }
        return arr;
    }

    private static void jsonArrayToFluidStacks(JsonArray arr, java.util.List<FluidStack> out) {
        for (JsonElement elem : arr) {
            JsonObject obj = elem.getAsJsonObject();
            String name = obj.get("fluid")
                .getAsString();
            int amount = obj.get("amount")
                .getAsInt();
            FluidStack fs = FluidRegistry.getFluidStack(name, amount);
            if (fs != null) out.add(fs);
        }
    }

    private static String voltageToTierName(long voltage) {
        if (voltage <= 0) return "";
        int tier = (int) Math.round(Math.log(voltage / 8.0) / Math.log(4));
        String[] names = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV",
            "MAX" };
        return tier >= 0 && tier < names.length ? names[tier] : "T" + tier;
    }

    private static long tierNameToVoltage(String name) {
        String[] names = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV",
            "MAX" };
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) return (long) (8 * Math.pow(4, i));
        }
        return 0;
    }

    private static JsonObject machineConfigToJson(MachineConfig cfg) {
        JsonObject obj = new JsonObject();
        if (cfg.speedBoostPercent != 100) obj.addProperty("speed", cfg.speedBoostPercent);
        if (cfg.maxParallel != 1) obj.addProperty("par", cfg.maxParallel);
        if (cfg.machineCount != 1) obj.addProperty("mach", cfg.machineCount);
        if (cfg.machineVoltage > 0) obj.addProperty("voltage", voltageToTierName(cfg.machineVoltage));
        if (cfg.machineAmperage != 1) obj.addProperty("amp", cfg.machineAmperage);
        if (cfg.perfectOC) obj.addProperty("poc", true);
        if (!cfg.inputConsumption.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (java.util.Map.Entry<Integer, Float> e : cfg.inputConsumption.entrySet()) {
                arr.add(new com.google.gson.JsonPrimitive(e.getKey() + ":" + e.getValue()));
            }
            obj.add("inMul", arr);
        }
        if (!cfg.outputProductivity.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (java.util.Map.Entry<Integer, Float> e : cfg.outputProductivity.entrySet()) {
                arr.add(new com.google.gson.JsonPrimitive(e.getKey() + ":" + e.getValue()));
            }
            obj.add("outMul", arr);
        }
        return obj;
    }

    private static void jsonToMachineConfig(JsonObject obj, MachineConfig cfg) {
        if (obj.has("speed")) cfg.speedBoostPercent = obj.get("speed")
            .getAsInt();
        if (obj.has("par")) cfg.maxParallel = obj.get("par")
            .getAsInt();
        if (obj.has("mach")) cfg.machineCount = obj.get("mach")
            .getAsInt();
        if (obj.has("voltage")) cfg.machineVoltage = tierNameToVoltage(
            obj.get("voltage")
                .getAsString());
        if (obj.has("amp")) cfg.machineAmperage = obj.get("amp")
            .getAsInt();
        if (obj.has("poc")) cfg.perfectOC = obj.get("poc")
            .getAsBoolean();
        // Legacy migration: if old "oc" field exists, convert to rough voltage
        if (obj.has("oc") && !obj.has("voltage")) {
            int oldOc = obj.get("oc")
                .getAsInt();
            if (oldOc > 0) {
                cfg.machineVoltage = 32 * (long) Math.pow(4, oldOc);
            }
        }
        if (obj.has("inMul")) {
            for (JsonElement e : obj.getAsJsonArray("inMul")) {
                String[] parts = e.getAsString()
                    .split(":");
                cfg.inputConsumption.put(Integer.parseInt(parts[0]), Float.parseFloat(parts[1]));
            }
        }
        if (obj.has("outMul")) {
            for (JsonElement e : obj.getAsJsonArray("outMul")) {
                String[] parts = e.getAsString()
                    .split(":");
                cfg.outputProductivity.put(Integer.parseInt(parts[0]), Float.parseFloat(parts[1]));
            }
        }
    }
}
