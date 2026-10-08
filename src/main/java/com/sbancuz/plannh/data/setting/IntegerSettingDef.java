package com.sbancuz.plannh.data.setting;

import java.util.function.Consumer;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.gui.tooltips.TooltipTheme;

import lombok.Getter;

@Getter
public class IntegerSettingDef extends SettingDef<Integer> {

    private static final int PADDING = 10;

    private final int min;
    private final int max;

    public IntegerSettingDef(final String key, final int def, final int min, final int max) {
        super(key, def);
        this.min = min;
        this.max = max;
    }

    @Override
    protected TooltipTheme.Role valueRole() {
        return TooltipTheme.Role.TUNABLE;
    }

    private int getMaxWidth() {
        return fieldWidth(min, max);
    }

    public static int fieldWidth(final int min, final int max) {
        return fieldWidth(Long.toString(widest(min, max)));
    }

    /** The same field, measured against a value that is not an int - a rate, say. */
    public static int fieldWidth(final String widest) {
        return TextRenderer.getFontRenderer()
            .getStringWidth(widest) + PADDING;
    }

    private static long widest(final int min, final int max) {
        return Math.max(Math.abs((long) min), Math.abs((long) max));
    }

    @Override
    public IWidget settingsWidget(final MachineConfig config, final Consumer<Runnable> edit) {
        return new TextFieldWidget().name("settings.int")
            .width(getMaxWidth())
            .value(new IntValue.Dynamic(() -> config.get(this), val -> edit.accept(() -> config.set(this, val))))
            .numbersInt(min, max)
            .formatAsInteger(true);
    }

    @Override
    protected Class<?> valueType() {
        return Integer.class;
    }
}
