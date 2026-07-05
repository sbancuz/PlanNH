package com.sbancuz.plannh.data;

import java.util.Objects;
import java.util.function.Function;

import net.minecraft.util.StatCollector;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder(builderMethodName = "emptyBuilder")
public class RecipeProperty<T> {

    @Getter
    private final String key;

    @Getter
    private final T defaultValue;

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

    public static <B> RecipePropertyBuilder<B, ?, ?> builder(final String key, final B defaultValue) {
        return RecipeProperty.<B>emptyBuilder()
            .key(key)
            .defaultValue(defaultValue);
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
