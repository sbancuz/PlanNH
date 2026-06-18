package com.sbancuz.plannh.data;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.annotation.Nonnull;

import net.minecraft.util.StatCollector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder(builderMethodName = "emptyBuilder")
public class RecipeProperty<T> {

    @Getter
    private final String key;

    @Getter
    private final T defaultValue;
    private final BiConsumer<JsonObject, T> serializer;
    private final Function<JsonObject, T> deserializer;

    @lombok.Builder.Default
    private final Function<T, String> displayFormatter = null;
    @lombok.Builder.Default
    private final Function<Float, String> amountFormatter = Objects::toString;

    public String displayName() {
        return StatCollector.translateToLocal("plannh.properties." + key);
    }

    public String formatDisplayName(final T value) {
        if (displayFormatter == null) return displayName();
        return displayFormatter.apply(value);
    }

    public String formatAmount(final float value) {
        return amountFormatter.apply(value);
    }

    @SuppressWarnings("unchecked")
    public void serialize(final JsonObject obj, final Object value) {
        serializer.accept(obj, (T) value);
    }

    public T deserialize(final JsonObject obj) {
        return deserializer.apply(obj);
    }

    public static <B> RecipePropertyBuilder<B, ?, ?> builder(final String key, final B defaultValue) {
        return RecipeProperty.<B>emptyBuilder()
            .key(key)
            .defaultValue(defaultValue);
    }

    public static RecipePropertyBuilder<Long, ?, ?> longBuilder(final String key, final Long defaultValue) {
        return RecipeProperty.builder(key, defaultValue)
            .serializer((obj, val) -> obj.addProperty(key, val))
            .deserializer(
                obj -> obj.has(key) ? obj.get(key)
                    .getAsLong() : defaultValue);
    }

    @Nonnull
    public static RecipePropertyBuilder<Integer, ?, ?> intBuilder(final String key, final int defaultValue) {
        return RecipeProperty.builder(key, defaultValue)
            .serializer((obj, val) -> obj.addProperty(key, val))
            .deserializer(
                obj -> obj.has(key) ? obj.get(key)
                    .getAsInt() : defaultValue);
    }

    @Nonnull
    public static RecipePropertyBuilder<String, ?, ?> stringBuilder(final String key, final String defaultValue) {
        return RecipeProperty.builder(key, defaultValue)
            .serializer((obj, val) -> obj.addProperty(key, val))
            .deserializer(
                obj -> obj.has(key) ? obj.get(key)
                    .getAsString() : defaultValue);
    }

    @Nonnull
    public static RecipePropertyBuilder<Float, ?, ?> floatBuilder(final String key, final float defaultValue) {
        return RecipeProperty.builder(key, defaultValue)
            .serializer((obj, val) -> obj.addProperty(key, val))
            .deserializer(
                obj -> obj.has(key) ? obj.get(key)
                    .getAsFloat() : defaultValue);
    }

    @Nonnull
    public static RecipePropertyBuilder<Boolean, ?, ?> boolBuilder(final String key, final boolean defaultValue) {
        return RecipeProperty.builder(key, defaultValue)
            .serializer((obj, val) -> obj.addProperty(key, val))
            .deserializer(
                obj -> obj.has(key) ? obj.get(key)
                    .getAsBoolean() : defaultValue);
    }

    @Nonnull
    public static RecipePropertyBuilder<int[], ?, ?> intArrayBuilder(final String key, final int[] defaultValue) {
        return RecipeProperty.builder(key, defaultValue)
            .serializer((obj, val) -> {
                final JsonArray arr = new JsonArray();
                for (final int v : val) arr.add(new JsonPrimitive(v));
                obj.add(key, arr);
            })
            .deserializer(obj -> {
                if (!obj.has(key)) return defaultValue.clone();
                final JsonArray arr = obj.getAsJsonArray(key);
                final int[] result = new int[arr.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = arr.get(i)
                        .getAsInt();
                }
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
