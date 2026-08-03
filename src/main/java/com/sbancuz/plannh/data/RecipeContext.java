package com.sbancuz.plannh.data;

import java.util.Map;

import javax.annotation.Nullable;

import com.sbancuz.plannh.data.properties.RecipeProperty;

public record RecipeContext(Map<RecipeProperty<?>, Object> properties) {

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getOrDefault(final RecipeProperty<T> prop, @Nullable final T defaultValue) {
        return (T) properties.getOrDefault(prop, defaultValue);
    }
}
