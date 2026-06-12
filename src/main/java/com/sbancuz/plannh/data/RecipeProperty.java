package com.sbancuz.plannh.data;

import java.util.function.BiConsumer;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import lombok.Getter;

public class RecipeProperty<T> {

    private final String key;
    @Getter
    private final String displayName;
    @Getter
    private final T defaultValue;
    private final BiConsumer<JsonObject, T> serializer;
    private final Function<JsonObject, T> deserializer;

    public RecipeProperty(final String key, final String displayName, final T defaultValue,
        final BiConsumer<JsonObject, T> serializer, final Function<JsonObject, T> deserializer) {
        this.key = key;
        this.displayName = displayName;
        this.defaultValue = defaultValue;
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    public void serialize(final JsonObject obj, final T value) {
        serializer.accept(obj, value);
    }

    public T deserialize(final JsonObject obj) {
        return deserializer.apply(obj);
    }

    public static RecipeProperty<Long> longProperty(final String key, final String displayName,
        final long defaultValue) {
        return new RecipeProperty<>(
            key,
            displayName,
            defaultValue,
            (obj, val) -> obj.addProperty(key, val),
            obj -> obj.has(key) ? obj.get(key)
                .getAsLong() : defaultValue);
    }

    public static RecipeProperty<Integer> intProperty(final String key, final String displayName,
        final int defaultValue) {
        return new RecipeProperty<>(
            key,
            displayName,
            defaultValue,
            (obj, val) -> obj.addProperty(key, val),
            obj -> obj.has(key) ? obj.get(key)
                .getAsInt() : defaultValue);
    }

    public static RecipeProperty<String> stringProperty(final String key, final String displayName,
        final String defaultValue) {
        return new RecipeProperty<>(
            key,
            displayName,
            defaultValue,
            (obj, val) -> obj.addProperty(key, val),
            obj -> obj.has(key) ? obj.get(key)
                .getAsString() : defaultValue);
    }

    public static RecipeProperty<Float> floatProperty(final String key, final String displayName,
        final float defaultValue) {
        return new RecipeProperty<>(
            key,
            displayName,
            defaultValue,
            (obj, val) -> obj.addProperty(key, val),
            obj -> obj.has(key) ? obj.get(key)
                .getAsFloat() : defaultValue);
    }

    public static RecipeProperty<Boolean> boolProperty(final String key, final String displayName,
        final boolean defaultValue) {
        return new RecipeProperty<>(
            key,
            displayName,
            defaultValue,
            (obj, val) -> obj.addProperty(key, val),
            obj -> obj.has(key) ? obj.get(key)
                .getAsBoolean() : defaultValue);
    }

    public static RecipeProperty<int[]> intArrayProperty(final String key, final String displayName,
        final int[] defaultValue) {
        return new RecipeProperty<>(key, displayName, defaultValue, (obj, val) -> {
            final JsonArray arr = new JsonArray();
            for (final int v : val) arr.add(new JsonPrimitive(v));
            obj.add(key, arr);
        }, obj -> {
            if (!obj.has(key)) return defaultValue.clone();
            final JsonArray arr = obj.getAsJsonArray(key);
            final int[] result = new int[arr.size()];
            for (int i = 0; i < result.length; i++) result[i] = arr.get(i)
                .getAsInt();
            return result;
        });
    }
}
