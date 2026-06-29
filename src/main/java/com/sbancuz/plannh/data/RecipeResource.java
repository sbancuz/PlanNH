package com.sbancuz.plannh.data;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

import lombok.experimental.SuperBuilder;

@SuperBuilder(builderMethodName = "emptyBuilder")
public class RecipeResource<T> extends RecipeProperty<T> {

    @lombok.Builder.Default
    private final ToIntFunction<T> amountExtractor = (v) -> 1;
    @lombok.Builder.Default
    private final BiConsumer<T, Integer> amountUpdater = (v, amount) -> {};

    @lombok.Builder.Default
    private final BiPredicate<T, T> connectionChecker = (a, b) -> true;
    @lombok.Builder.Default
    private final ToIntFunction<T> hashCodeExtractor = Objects::hashCode;

    @Override
    public String displayName() {
        return getKey();
    }

    public int extractAmount(final T value) {
        return amountExtractor.applyAsInt(value);
    }

    public void setAmount(final T value, final int newAmount) {
        amountUpdater.accept(value, newAmount);
    }

    public boolean canConnect(final T a, final T b) {
        return connectionChecker.test(a, b);
    }

    public int hashValue(final T value) {
        return hashCodeExtractor.applyAsInt(value);
    }

    public static <B> RecipeResourceBuilder<B, ?, ?> builder(final String key, final B defaultValue) {
        return RecipeResource.<B>emptyBuilder()
            .key(key)
            .defaultValue(defaultValue);
    }
}
