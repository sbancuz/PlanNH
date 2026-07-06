package com.sbancuz.plannh.gui.components;

import java.util.function.Consumer;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.widgets.ButtonWidget;

public class CycleButton<E extends Enum<E>> extends ButtonWidget<CycleButton<E>> {

    private final E[] values;
    private E current;
    @Nullable
    private Function<E, IKey> overlayFn;
    @Nullable
    private Consumer<E> onCycle;

    public CycleButton(final Class<E> enumClass) {
        this.values = enumClass.getEnumConstants();
    }

    public CycleButton<E> overlay(final Function<E, IKey> overlayFn) {
        this.overlayFn = overlayFn;
        return this;
    }

    public CycleButton<E> current(final E value) {
        this.current = value;
        if (overlayFn != null) {
            overlay(overlayFn.apply(value));
        }
        return this;
    }

    public CycleButton<E> onCycle(@Nullable final Consumer<E> onCycle) {
        this.onCycle = onCycle;
        return this;
    }

    public E cycle(final E old) {
        final int nextOrdinal = (old.ordinal() + 1) % values.length;
        current = values[nextOrdinal];
        if (overlayFn != null) {
            overlay(overlayFn.apply(current));
        }
        return current;
    }

    public static String shortName(final Enum<?> e) {
        final String name = e.name();
        return name.length() <= 4 ? name : name.substring(0, 4);
    }

    @Override
    public @NotNull Result onMousePressed(final int mouseButton) {
        if (onCycle == null) return Result.IGNORE;
        final E next = cycle(current);
        onCycle.accept(next);
        return Result.SUCCESS;
    }
}
