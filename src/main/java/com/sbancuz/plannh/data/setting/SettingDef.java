package com.sbancuz.plannh.data.setting;

import java.lang.reflect.Type;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.gui.common.TooltipStyle;

import lombok.Getter;

@Getter
public abstract class SettingDef<T> implements JsonSerializer<Object>, JsonDeserializer<Object> {

    protected final String key;
    protected final String label;
    protected final T defaultValue;

    protected SettingDef(final String key, final T defaultValue) {
        this.key = key;
        this.label = StatCollector.translateToLocal("plannh.settings." + key);
        this.defaultValue = defaultValue;

        Settings.register(this);
    }

    /** This setting's line on a node tooltip, or null when the value it holds has nothing to say. */
    public String tooltip(final T value) {
        return TooltipStyle.entry(getLabel(), valueColour(), String.valueOf(value));
    }

    protected EnumChatFormatting valueColour() {
        return TooltipStyle.IDENTITY;
    }

    /**
     * Builds the widget that edits this setting's value.
     *
     * <p>
     * The widget holds the value and the config, but not the chart the setting belongs to - and a
     * setting is a solve input, not a widget-local preference: the machine count, the overclock tier
     * and the heat all decide what the balancer comes back with. So the panel owns the consequence
     * and the widget hands {@code edit} the change itself: the panel runs it (so undo reaches it),
     * re-solves, saves, and rebuilds the rows, because a value can decide what else is on offer.
     */
    public abstract IWidget settingsWidget(MachineConfig config, Consumer<Runnable> edit);

    @Override
    public final JsonElement serialize(final Object value, final Type typeOfSrc,
        final JsonSerializationContext context) {
        return context.serialize(value);
    }

    @Override
    public final @Nullable Object deserialize(final JsonElement json, final Type typeOfT,
        final JsonDeserializationContext context) {
        return context.deserialize(json, valueType());
    }

    /** The class a stored value is read back as: an int, a boolean, or the enum this def cycles. */
    protected abstract Class<?> valueType();

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SettingDef<?>setting)) return false;
        return key.equals(setting.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
