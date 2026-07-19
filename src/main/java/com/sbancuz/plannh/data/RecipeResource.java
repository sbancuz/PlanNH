package com.sbancuz.plannh.data;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder(builderMethodName = "emptyBuilder")
public class RecipeResource<T> extends RecipeProperty<T> {

    @lombok.Builder.Default
    private final ToIntFunction<T> amountExtractor = (v) -> 1;
    @lombok.Builder.Default
    private final BiConsumer<T, Integer> amountUpdater = (v, amount) -> {};

    @lombok.Builder.Default
    private final BiPredicate<T, T> connectionChecker = (a, b) -> true;
    @lombok.Builder.Default
    private final ToIntFunction<T> hashCodeExtractor = Objects::hashCode;

    @lombok.Builder.Default
    private final Function<T, ItemStack> displayStackProvider = (v) -> null;
    @lombok.Builder.Default
    private final BiPredicate<T, ItemStack> lookupMatcher = (v, stack) -> false;
    @lombok.Builder.Default
    private final ToIntFunction<T> colorProvider = (v) -> -1;

    // Pin/arrow colors used when no per-value color is derivable. Opaque white so a resource
    // type that declares none still renders visibly instead of vanishing.
    @Getter
    @lombok.Builder.Default
    private final int pinInputColor = 0xFFFFFFFF;
    @Getter
    @lombok.Builder.Default
    private final int pinOutputColor = 0xFFFFFFFF;
    @Getter
    @lombok.Builder.Default
    private final int arrowColor = 0xFFFFFFFF;

    @Override
    public String displayName() {
        return getKey();
    }

    public int extractAmount(final T value) {
        return amountExtractor.applyAsInt(value);
    }

    public void setAmount(final T value, final int newAmount) {
        amountUpdater.accept(value, newAmount);
    }

    public boolean canConnect(final T a, final T b) {
        return connectionChecker.test(a, b);
    }

    public int hashValue(final T value) {
        return hashCodeExtractor.applyAsInt(value);
    }

    /** The ItemStack recipe viewers (NEI) show for this value; null if it has none. */
    @Nullable
    public ItemStack displayStack(final T value) {
        return displayStackProvider.apply(value);
    }

    /** Whether an NEI lookup stack refers to this value. */
    public boolean matchesLookup(final T value, final ItemStack lookup) {
        return lookupMatcher.test(value, lookup);
    }

    /** Representative bare-RGB color for this value, or -1 when none is derivable. */
    public int color(final T value) {
        return colorProvider.applyAsInt(value);
    }

    public static <B> RecipeResourceBuilder<B, ?, ?> builder(final String key, final B defaultValue) {
        return RecipeResource.<B>emptyBuilder()
            .key(key)
            .defaultValue(defaultValue);
    }
}
