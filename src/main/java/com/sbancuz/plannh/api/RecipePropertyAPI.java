package com.sbancuz.plannh.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    static {
        registerProperty(DURATION_TICKS);
        registerProperty(TOTAL_EU);
        registerProperty(EU_PER_TICK);
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
