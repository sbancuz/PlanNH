package com.sbancuz.plannh.data.serialization;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.setting.SettingDef;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class MachineConfigAdapter implements JsonSerializer<MachineConfig>, JsonDeserializer<MachineConfig> {

    @Override
    public JsonElement serialize(MachineConfig src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        MachineProfile profile = src.getProfile();

        if (!MachineProfileRegistry.defaultId()
            .equals(src.getProfileId())) {
            obj.addProperty("profile", src.getProfileId());
        }

        JsonObject settingsObj = new JsonObject();
        for (SettingDef<?> def : profile.settings()) {
            Object val = src.getSettings().get(def.getKey());
            if (val == null || val.equals(def.getDefaultValue())) continue;
            switch (val) {
                case Boolean b -> settingsObj.addProperty(def.getKey(), b);
                case Integer i -> settingsObj.addProperty(def.getKey(), i);
                case String s -> settingsObj.addProperty(def.getKey(), s);
                default -> {
                }
            }
        }
        obj.add("settings", settingsObj);

        obj.add("inputConsumption", Serializer.GSON.toJsonTree(src.getInputConsumption()));
        obj.add("outputProductivity", Serializer.GSON.toJsonTree(src.getOutputProductivity()));

        return obj;
    }

    @Override
    public MachineConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        MachineProfile p = MachineProfileRegistry.get(obj.get("profile").getAsString());

        Map<String, Object> settings = new HashMap<>();
        JsonObject settingsObj = obj.getAsJsonObject("settings");
        for (Map.Entry<String, JsonElement> entry : settingsObj.entrySet()) {
            JsonElement el = entry.getValue();
            if (el.isJsonPrimitive()) {
                JsonPrimitive prim = el.getAsJsonPrimitive();
                if (prim.isBoolean()) settings.put(entry.getKey(), prim.getAsBoolean());
                else if (prim.isNumber()) settings.put(entry.getKey(), prim.getAsInt());
                else settings.put(entry.getKey(), prim.getAsString());
            }
        }

        Type mapType = new TypeToken<Map<Integer, Float>>(){}.getType();

        return new MachineConfig(p != null ? p : MachineProfileRegistry.get(MachineProfileRegistry.defaultId()),
            settings,
            Serializer.GSON.fromJson(obj.get("inputConsumption"), mapType),
            Serializer.GSON.fromJson(obj.get("outputProductivity"), mapType));
    }
}
