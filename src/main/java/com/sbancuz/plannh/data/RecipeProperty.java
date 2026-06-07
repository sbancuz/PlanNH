package com.sbancuz.plannh.data;

import java.util.function.BiConsumer;
import java.util.function.Function;

import com.google.gson.JsonObject;

import lombok.Getter;

public class RecipeProperty<T> {

    private final String key;
    @Getter
    private final String displayName;
    @Getter
    private final T defaultValue;
    private final BiConsumer<JsonObject, T> serializer;
    private final Function<JsonObject, T> deserializer;

    public RecipeProperty(String key, String displayName, T defaultValue, BiConsumer<JsonObject, T> serializer,
        Function<JsonObject, T> deserializer) {
        this.key = key;
        this.displayName = displayName;
        this.defaultValue = defaultValue;
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    public void serialize(JsonObject obj, T value) {
        serializer.accept(obj, value);
    }

    public T deserialize(JsonObject obj) {
        return deserializer.apply(obj);
    }

    public static RecipeProperty<Long> longProperty(String key, String displayName, long defaultValue) {
        return new RecipeProperty<>(
            key,
            displayName,
            defaultValue,
            (obj, val) -> obj.addProperty(key, val),
            obj -> obj.has(key) ? obj.get(key)
                .getAsLong() : defaultValue);
    }

    public static RecipeProperty<Integer> intProperty(String key, String displayName, int defaultValue) {
        return new RecipeProperty<>(
            key,
            displayName,
            defaultValue,
            (obj, val) -> obj.addProperty(key, val),
            obj -> obj.has(key) ? obj.get(key)
                .getAsInt() : defaultValue);
    }

    public static RecipeProperty<String> stringProperty(String key, String displayName, String defaultValue) {
        return new RecipeProperty<>(
            key,
            displayName,
            defaultValue,
            (obj, val) -> obj.addProperty(key, val),
            obj -> obj.has(key) ? obj.get(key)
                .getAsString() : defaultValue);
    }

    public static RecipeProperty<Float> floatProperty(String key, String displayName, float defaultValue) {
        return new RecipeProperty<>(
            key,
            displayName,
            defaultValue,
            (obj, val) -> obj.addProperty(key, val),
            obj -> obj.has(key) ? obj.get(key)
                .getAsFloat() : defaultValue);
    }
}
