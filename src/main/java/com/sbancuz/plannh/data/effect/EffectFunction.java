package com.sbancuz.plannh.data.effect;

import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeContext;

@FunctionalInterface
public interface EffectFunction<T> {

    T apply(EffectResult current, MachineConfig config, RecipeContext ctx);

}
