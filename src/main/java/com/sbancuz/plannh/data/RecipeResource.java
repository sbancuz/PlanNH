package com.sbancuz.plannh.data;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import com.google.gson.JsonObject;

public class RecipeResource<T> extends RecipeProperty<T> {

    private final Function<T, String> displayFormatter;
    private final ToIntFunction<T> amountExtractor;
    private final BiPredicate<T, T> connectionChecker;
    private final ToIntFunction<T> hashCodeExtractor;

    RecipeResource(final String key, final String displayName, final T defaultValue,
        final BiConsumer<JsonObject, T> serializer, final Function<JsonObject, T> deserializer,
        final Function<T, String> displayFormatter, final ToIntFunction<T> amountExtractor,
        final BiPredicate<T, T> connectionChecker, final ToIntFunction<T> hashCodeExtractor) {
        super(key, displayName, defaultValue, serializer, deserializer);
        this.displayFormatter = displayFormatter;
        this.amountExtractor = amountExtractor;
        this.connectionChecker = connectionChecker;
        this.hashCodeExtractor = hashCodeExtractor;
    }

    public String formatDisplayName(final T value) {
        return displayFormatter.apply(value);
    }

    public int extractAmount(final T value) {
        return amountExtractor.applyAsInt(value);
    }

    public boolean canConnect(final T a, final T b) {
        return connectionChecker.test(a, b);
    }

    public int hashValue(final T value) {
        return hashCodeExtractor.applyAsInt(value);
    }

    public static <T> Builder<T> builder(final String key, final String displayName, final T defaultValue) {
        return new Builder<>(key, displayName, defaultValue);
    }

    public static class Builder<T> {

        private final String key;
        private final String displayName;
        private final T defaultValue;
        private BiConsumer<JsonObject, T> serializer;
        private Function<JsonObject, T> deserializer;
        private Function<T, String> displayFormatter;
        private ToIntFunction<T> amountExtractor = v -> 1;
        private BiPredicate<T, T> connectionChecker = (a, b) -> true;
        private ToIntFunction<T> hashCodeExtractor = Object::hashCode;

        Builder(final String key, final String displayName, final T defaultValue) {
            this.key = key;
            this.displayName = displayName;
            this.defaultValue = defaultValue;
            this.displayFormatter = v -> displayName;
        }

        public Builder<T> serialize(final BiConsumer<JsonObject, T> serializer) {
            this.serializer = serializer;
            return this;
        }

        public Builder<T> deserialize(final Function<JsonObject, T> deserializer) {
            this.deserializer = deserializer;
            return this;
        }

        public Builder<T> displayFormatter(final Function<T, String> displayFormatter) {
            this.displayFormatter = displayFormatter;
            return this;
        }

        public Builder<T> amountExtractor(final ToIntFunction<T> amountExtractor) {
            this.amountExtractor = amountExtractor;
            return this;
        }

        public Builder<T> connectionChecker(final BiPredicate<T, T> connectionChecker) {
            this.connectionChecker = connectionChecker;
            return this;
        }

        public Builder<T> hashCodeExtractor(final ToIntFunction<T> hashCodeExtractor) {
            this.hashCodeExtractor = hashCodeExtractor;
            return this;
        }

        public RecipeResource<T> build() {
            return new RecipeResource<>(
                key,
                displayName,
                defaultValue,
                serializer,
                deserializer,
                displayFormatter,
                amountExtractor,
                connectionChecker,
                hashCodeExtractor);
        }
    }
}
