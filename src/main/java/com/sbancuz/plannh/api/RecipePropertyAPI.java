package com.sbancuz.plannh.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.sbancuz.plannh.data.RecipeProperty;
import com.sbancuz.plannh.data.RecipePropertyExtractor;

public class RecipePropertyAPI {

    private static final List<RecipeProperty<?>> properties = new ArrayList<>();
    private static final List<RecipePropertyExtractor> extractors = new ArrayList<>();

    // Built-in property constants
    public static final RecipeProperty<Integer> DURATION_TICKS = RecipeProperty
        .intProperty("durationTicks", "Duration", 0);
    public static final RecipeProperty<Long> TOTAL_EU = RecipeProperty.longProperty("totalEu", "Total EU", 0L);
    public static final RecipeProperty<Long> EU_PER_TICK = RecipeProperty.longProperty("euPerTick", "EU/t", 0L);
    public static final RecipeProperty<float[]> OUTPUT_CHANCES = new RecipeProperty<>(
        "outputChances",
        "Output Chances",
        new float[0],
        (obj, val) -> {
            JsonArray arr = new JsonArray();
            for (float f : val) arr.add(new JsonPrimitive(f));
            obj.add("outputChances", arr);
        },
        obj -> {
            if (!obj.has("outputChances")) return new float[0];
            JsonArray arr = obj.getAsJsonArray("outputChances");
            float[] result = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) result[i] = arr.get(i)
                .getAsFloat();
            return result;
        });

    static {
        registerProperty(DURATION_TICKS);
        registerProperty(TOTAL_EU);
        registerProperty(EU_PER_TICK);
        registerProperty(OUTPUT_CHANCES);
    }

    public static void registerProperty(RecipeProperty<?> property) {
        properties.add(property);
    }

    public static void registerExtractor(RecipePropertyExtractor extractor) {
        extractors.add(extractor);
    }

    public static List<RecipeProperty<?>> getProperties() {
        return Collections.unmodifiableList(properties);
    }

    public static List<RecipePropertyExtractor> getExtractors() {
        return Collections.unmodifiableList(extractors);
    }
}
