package com.sbancuz.plannh.data.serialization;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.setting.SettingDef;
import com.sbancuz.plannh.data.setting.Settings;

public class MachineConfigAdapter implements JsonSerializer<MachineConfig>, JsonDeserializer<MachineConfig> {

    @Override
    public JsonElement serialize(MachineConfig src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        MachineProfile profile = src.getProfile();

        obj.addProperty("profile", src.getProfileId());

        JsonObject settingsObj = new JsonObject();
        for (SettingDef<?> def : profile.defs()) {
            final Object val = src.get(def);
            if (val == null || val.equals(def.getDefaultValue())) continue;
            final JsonElement saved = def.serialize(val, Object.class, context);
            if (saved != null && !saved.isJsonNull()) settingsObj.add(def.getKey(), saved);
        }
        obj.add("settings", settingsObj);

        return obj;
    }

    @Override
    public MachineConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        MachineProfile p = MachineProfileRegistry.get(
            obj.get("profile")
                .getAsString());

        Map<SettingDef<?>, Object> settings = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("settings")
            .entrySet()) {
            final SettingDef<?> setting = Settings.get(entry.getKey());
            if (setting == null) continue;
            final Object value = setting.deserialize(entry.getValue(), Object.class, context);
            if (value != null) settings.put(setting, value);
        }

        return new MachineConfig(p, settings);
    }
}
