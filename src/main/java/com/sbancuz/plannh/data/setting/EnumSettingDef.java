package com.sbancuz.plannh.data.setting;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.value.EnumValue;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeContext;

import lombok.Getter;

@Getter
public class EnumSettingDef<E extends Enum<E>> extends SettingDef<E> {

    private final Class<E> type;

    public EnumSettingDef(String key, E defaultValue, Class<E> type,
        @Nullable BiFunction<E, MachineConfig, String> badgeFn,
        BiPredicate<RecipeContext, Map<String, Object>> visibility) {
        super(key, defaultValue, badgeFn, visibility);
        this.type = type;
    }

    public EnumSettingDef(String key, E defaultValue, Class<E> type,
        @Nullable BiFunction<E, MachineConfig, String> badgeFn) {
        this(key, defaultValue, type, badgeFn, (_, _) -> true);
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
    public IWidget settingsWidget(MachineConfig config) {
        return new CycleButtonWidget().value(new EnumValue.Dynamic<>(type, () -> {
            E val = config.getEnum(key, type);
            return val != null ? val : type.getEnumConstants()[0];
        }, val -> config.setEnum(key, val)))
            .child(
                IKey.str("ERROR")
                    .asWidget()
                    .setEnabledIf(_ -> config.getEnum(key, type) == null))
            .width(getMaxWidth()); // this throws if
    }
}
