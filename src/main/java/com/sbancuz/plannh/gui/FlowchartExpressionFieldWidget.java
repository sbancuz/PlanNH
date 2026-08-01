package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.gtnewhorizon.gtnhlib.util.parsing.MathExpressionParser;

public abstract class FlowchartExpressionFieldWidget extends FlowchartTextFieldWidget {

    protected static final MathExpressionParser.Context ctx = new MathExpressionParser.Context().setEmptyValue(0)
        .setErrorValue(0);
    protected double result;

    public FlowchartExpressionFieldWidget(FlowchartWidget<?, ?> parent, final double initialValue) {
        super(parent);
        this.result = initialValue;
        handler.getText()
            .add(String.valueOf((int) this.result));
    }

    @Override
    public void onRemoveFocus(final ModularGuiContext context) {
        super.onRemoveFocus(context);
        final String text = handler.getTextAsString();
        if (!text.isEmpty()) {
            this.result = MathExpressionParser.parse(text, ctx);
        }
        handler.getText()
            .clear();
        handler.getText()
            .add(String.valueOf((int) this.result));
    }
}
