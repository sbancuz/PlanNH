package com.sbancuz.plannh.data.effect;

import java.util.Map;

import com.sbancuz.plannh.data.RecipeContext;

@FunctionalInterface
public interface EffectFunction<T> {

    T apply(EffectResult current, Map<String, Object> settings, RecipeContext ctx);

}
