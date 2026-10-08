package com.sbancuz.plannh.data.serialization;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.flowchart.balancer.Pin;
import com.sbancuz.plannh.data.setting.SettingDef;
import com.sbancuz.plannh.data.setting.Settings;

/**
 * Writes a machine config as its profile, the settings that differ from that profile, and the
 * targets the balancer holds the machine to.
 *
 * <p>
 * The targets are here rather than left to reflection because the config has this adapter at all:
 * a hand-written one has to name every field it means to keep, and these three moved onto the
 * config from the node that owns it.
 */
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

        // Which target counts, when there is one. Absent means the balance picks the machine's own setting.
        final Pin kind = src.getTargetKind();
        if (kind != null) obj.addProperty("targetKind", kind.name());

        // The targets themselves, always remembered whether or not they are the one in use: the
        // switcher shows the other kind's number, so it has to survive a save either way.
        final JsonObject copies = new JsonObject();
        copies.addProperty(
            "copies",
            src.getCopies()
                .copies());
        obj.add("copies", copies);

        final JsonObject ratesObj = new JsonObject();
        for (final Map.Entry<Integer, Double> entry : src.getRates()
            .rates()
            .entrySet()) {
            final Double rate = entry.getValue();
            if (rate == null || rate <= 0) continue;
            ratesObj.add(String.valueOf(entry.getKey()), new JsonPrimitive(rate));
        }
        if (!ratesObj.entrySet()
            .isEmpty()) obj.add("rates", ratesObj);

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

        final MachineConfig config = new MachineConfig(p, settings);

        final JsonElement kind = obj.get("targetKind");
        if (kind != null && kind.isJsonPrimitive()) {
            final String name = legacyPin(kind.getAsString());
            try {
                config.setTargetKind(Pin.valueOf(name));
            } catch (final IllegalArgumentException e) {
                // A save from a build that knew a kind this one does not. The machine runs unpinned
                // rather than the save failing to load.
                PlanNH.LOG.warn("Unknown balance target {}; leaving the machine unpinned", kind);
            }
        }

        final Integer copies = readCopies(obj);
        if (copies != null) config.getCopies()
            .setCopies(copies);

        final JsonElement rates = obj.get("rates");
        if (rates != null && rates.isJsonObject()) {
            for (final Map.Entry<String, JsonElement> entry : rates.getAsJsonObject()
                .entrySet()) {
                try {
                    final double rate = entry.getValue()
                        .getAsDouble();
                    if (rate > 0) config.getRates()
                        .rates()
                        .put(Integer.valueOf(entry.getKey()), rate);
                } catch (final NumberFormatException | UnsupportedOperationException ignored) {
                    // A port index that is not one. Dropping the entry keeps the rest of the targets.
                }
            }
        }

        return config;
    }

    /**
     * A pin name as it was before the copy target was renamed out of "machine count". Plans are
     * shareable, so a save written by an older build has to keep its pin rather than silently
     * dropping to unpinned.
     */
    private static String legacyPin(final String name) {
        return "FIXED_COUNT".equals(name) ? "FIXED_COPIES" : name;
    }

    /** The pinned copy count, from the current key or from either key an older save wrote. */
    private static @Nullable Integer readCopies(final JsonObject obj) {
        for (final String[] path : new String[][] { { "copies", "copies" }, { "count", "machines" },
            { "count", "copies" } }) {
            final JsonElement outer = obj.get(path[0]);
            if (outer == null || !outer.isJsonObject()) continue;
            final JsonElement inner = outer.getAsJsonObject()
                .get(path[1]);
            if (inner != null && inner.isJsonPrimitive()) return inner.getAsInt();
        }
        return null;
    }
}
