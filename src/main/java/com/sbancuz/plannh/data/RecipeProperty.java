package com.sbancuz.plannh.data;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.annotation.Nonnull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import lombok.Getter;

public class RecipeProperty<T> {

    @Getter
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

    @SuppressWarnings("unchecked")
    public void serialize(final JsonObject obj, final Object value) {
        serializer.accept(obj, (T) value);
    }

    public T deserialize(final JsonObject obj) {
        return deserializer.apply(obj);
    }

    @Nonnull
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

    @Nonnull
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

    @Nonnull
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

    @Nonnull
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

    @Nonnull
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

    @Nonnull
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

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        final RecipeProperty<?> that = (RecipeProperty<?>) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
