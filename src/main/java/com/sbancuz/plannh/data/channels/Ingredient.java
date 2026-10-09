package com.sbancuz.plannh.data.channels;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public sealed interface Ingredient permits Ingredient.Item,Ingredient.Fluid {

    record Item(@Nonnull String id, int meta, @Nullable String nbt) implements Ingredient {}

    record Fluid(@Nonnull String name) implements Ingredient {}
}
