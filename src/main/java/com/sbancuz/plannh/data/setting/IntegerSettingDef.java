package com.sbancuz.plannh.data.setting;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import javax.annotation.Nullable;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeContext;

import lombok.Getter;

@Getter
public class IntegerSettingDef extends SettingDef<Integer> {

    private final int min;
    private final int max;

    public IntegerSettingDef(final String key, final int def, final int min, final int max,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn,
        BiPredicate<RecipeContext, Map<String, Object>> visibility) {
        super(key, def, badgeFn, visibility);
        this.min = min;
        this.max = max;
    }

    public IntegerSettingDef(final String key, final int def, final int min, final int max,
        @Nullable final BiFunction<Integer, MachineConfig, String> badgeFn) {
        this(key, def, min, max, badgeFn, (_, _) -> true);
    }

    public IntegerSettingDef(final String key, final int def, final int min, final int max) {
        this(key, def, min, max, null);
    }

    private int getMaxWidth() {
        return (int) Math.log10(Math.max(Math.abs(min), Math.abs(max))) * 10;
    }

    @Override
    public IWidget settingsWidget(MachineConfig config) {
        return new TextFieldWidget().width(getMaxWidth())
            .value(new IntValue.Dynamic(() -> config.getInt(key), val -> config.setInt(key, val)))
            .numbersInt(min, max)
            .formatAsInteger(true);
    }
}
