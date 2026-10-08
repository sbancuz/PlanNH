package com.sbancuz.plannh.data.setting;

import java.util.function.Consumer;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.gui.tooltips.TooltipBuilder;

public class BooleanSettingDef extends SettingDef<Boolean> {

    public BooleanSettingDef(String key, Boolean defaultValue) {
        super(key, defaultValue);
    }

    @Override
    public Consumer<TooltipBuilder> tooltip(final Boolean value) {
        return value ? out -> out.flag(getLabel()) : out -> {};
    }

    @Override
    public IWidget settingsWidget(final MachineConfig config, final Consumer<Runnable> edit) {
        return new ToggleButton().name("settings.toggle")
            .value(new BoolValue.Dynamic(() -> config.get(this), val -> edit.accept(() -> config.set(this, val))))
            .overlay(false, IKey.str("[ ]"))
            .overlay(true, IKey.str("[✓]"));
    }

    @Override
    protected Class<?> valueType() {
        return Boolean.class;
    }
}
