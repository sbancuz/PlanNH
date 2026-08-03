package com.sbancuz.plannh.gui.step;

import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.utils.Alignment;
import com.sbancuz.plannh.data.flowchart.Step;
import com.sbancuz.plannh.gui.FlowchartExpressionFieldWidget;
import com.sbancuz.plannh.gui.PlannhColors;

public class AmountTextFieldWidget extends FlowchartExpressionFieldWidget {

    private final Step data;

    public AmountTextFieldWidget(StepWidget parent) {
        super(
            parent,
            parent.getData()
                .getAmount());

        data = parent.getData();

        width(50);
        height(16);
        setScale(1f);
        // TODO: Figure out why I can't use it
        // expanded();

        setEnabledIf(_ -> data.getFilter() != null);
        background(
            new DynamicDrawable(
                () -> new Rectangle()
                    .color(isEditing ? PlannhColors.NOTE_BG_EDITING.getColor() : PlannhColors.TRANSPARENT.getColor())));

        setTextAlignment(Alignment.CENTER);

        updateTextColor();
    }

    @Override
    public void onFocus(final ModularGuiContext context) {
        super.onFocus(context);
        setTextAlignment(Alignment.CenterLeft);

        setTextColor(PlannhColors.TEXT_BLACK.getColor());
    }

    @Override
    protected void onTextChanged() {
        super.onTextChanged();
    }

    @Override
    public boolean canScrollHorizontally() {
        return false;
    }

    @Override
    public void onRemoveFocus(final ModularGuiContext context) {
        super.onRemoveFocus(context);
        data.setAmount((int) this.result);
        updateTextColor();
        setTextAlignment(Alignment.CENTER);
    }

    private void updateTextColor() {
        setTextColor(switch (Integer.signum((int) this.result)) {
            case 1 -> PlannhColors.ACCENT_GREEN.getColor();
            case -1 -> PlannhColors.ACCENT_RED.getColor();
            case 0 -> PlannhColors.TEXT_BLACK.getColor();
            default -> throw new AssertionError("Unexpected value: " + Integer.signum((int) this.result));
        });
    }
}
