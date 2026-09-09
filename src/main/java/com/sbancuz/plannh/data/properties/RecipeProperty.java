package com.sbancuz.plannh.data.properties;

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

    @Getter
    @lombok.Builder.Default
    private final boolean perSec = false;

    @lombok.Builder.Default
    private final Function<T, String> displayFormatter = null;

    public String displayName() {
        return StatCollector.translateToLocal("plannh.properties." + key);
    }

    public String formatDisplayName(final T value) {
        if (displayFormatter == null) return displayName();
        return displayFormatter.apply(value);
    }

    public static <T> RecipePropertyBuilder<T, ?, ?> builder(final String key, final T defaultValue) {
        return RecipeProperty.<T>emptyBuilder()
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
