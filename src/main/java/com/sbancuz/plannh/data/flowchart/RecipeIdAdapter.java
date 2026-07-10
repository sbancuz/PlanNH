package com.sbancuz.plannh.data.flowchart;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import codechicken.nei.recipe.Recipe;

public class RecipeIdAdapter implements JsonSerializer<Recipe.RecipeId>, JsonDeserializer<Recipe.RecipeId> {

    @Override
    public Recipe.RecipeId deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        return Recipe.RecipeId.of(json.getAsJsonObject());
    }

    @Override
    public JsonElement serialize(Recipe.RecipeId src, Type typeOfSrc, JsonSerializationContext context) {
        return src.toJsonObject();
    }
}
