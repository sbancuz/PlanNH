package com.sbancuz.plannh.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ExtractedProperties {

    private final Map<RecipeProperty<?>, Object> values = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(RecipeProperty<T> property) {
        if (values.containsKey(property)) {
            return (T) values.get(property);
        }
        return property.getDefaultValue();
    }

    public <T> void set(RecipeProperty<T> property, T value) {
        if (value != null && !value.equals(property.getDefaultValue())) {
            values.put(property, value);
        } else {
            values.remove(property);
        }
    }

    public void putAll(Map<RecipeProperty<?>, Object> map) {
        values.putAll(map);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Set<Map.Entry<RecipeProperty<?>, Object>> entrySet() {
        return Collections.unmodifiableSet(values.entrySet());
    }

    public Map<RecipeProperty<?>, Object> asMap() {
        return Collections.unmodifiableMap(values);
    }
}
