package com.sbancuz.plannh.data.setting;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.gui.common.TooltipStyle;

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
    protected EnumChatFormatting valueColour() {
        return TooltipStyle.TUNABLE;
    }

    private int getMaxWidth() {
        final long widest = Math.max(Math.abs((long) min), Math.abs((long) max));
        return TextRenderer.getFontRenderer()
            .getStringWidth(Long.toString(widest)) + PADDING;
    }

    @Override
    public IWidget settingsWidget(final MachineConfig config, final SettingEdit edit) {
        return new TextFieldWidget().width(getMaxWidth())
            .value(new IntValue.Dynamic(() -> config.get(this), val -> edit.apply(() -> config.set(this, val))))
            .numbersInt(min, max)
            .formatAsInteger(true);
    }

    @Override
    protected Class<?> valueType() {
        return Integer.class;
    }
}
