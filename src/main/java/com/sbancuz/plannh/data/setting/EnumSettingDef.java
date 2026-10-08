package com.sbancuz.plannh.data.setting;

import java.util.Arrays;
import java.util.function.Consumer;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.value.EnumValue;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.sbancuz.plannh.data.MachineConfig;

import lombok.Getter;

@Getter
public class EnumSettingDef<E extends Enum<E>> extends SettingDef<E> {

    private final Class<E> type;

    public EnumSettingDef(String key, E defaultValue, Class<E> type) {
        super(key, defaultValue);
        this.type = type;
    }

    private int getMaxWidth() {
        return Arrays.stream(type.getEnumConstants())
            .map(Enum::toString)
            .mapToInt(
                s -> TextRenderer.getFontRenderer()
                    .getStringWidth(s))
            .max()
            .orElseThrow() + 10;
    }

    @Override
    public IWidget settingsWidget(final MachineConfig config, final Consumer<Runnable> edit) {
        final CycleButtonWidget button = new CycleButtonWidget().name("settings.cycle")
            .value(new EnumValue.Dynamic<>(type, () -> {
                E val = config.get(this);
                return val != null ? val : type.getEnumConstants()[0];
            }, val -> edit.accept(() -> config.set(this, val))))
            .width(getMaxWidth());
        for (final E constant : type.getEnumConstants()) {
            button.stateOverlay(constant, IKey.str(constant.name()));
        }
        return button;
    }

    @Override
    protected Class<?> valueType() {
        return type;
    }
}
