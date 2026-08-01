package com.sbancuz.plannh.gui.step;

import java.util.Map;
import java.util.UUID;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.flowchart.Step;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.CloseButtonWidget;
import com.sbancuz.plannh.gui.FlowchartFlow;
import com.sbancuz.plannh.gui.FlowchartWidget;
import com.sbancuz.plannh.gui.HeaderTextWidget;
import com.sbancuz.plannh.gui.PlannhColors;

public class StepWidget extends FlowchartWidget<StepWidget, Step> {

    private static final UITexture bg = UITexture.builder()
        .location("nei:textures/gui/recipebg.png")
        .imageSize(256, 256)
        .subAreaXYWH(4, 4, 176, 166)
        .adaptable(3)
        .build();

    private static final int PORT_SIZE = 8;
    private static final int PORT_HALF = 4;
    private static final int EST_W = 70 + 10;
    private static final int EST_H = 106;

    public StepWidget(final CanvasWidget canvas, final Step data) {
        super(canvas, data);
        coverChildren();
        background(bg);
        padding(5);

        Flow mainColumn = FlowchartFlow.column(this)
            .coverChildren();

        Flow topRow = FlowchartFlow.row(this)
            .coverChildrenHeight()
            .childPadding(4)
            .fullWidth()
            // TODO
            .background(new Rectangle().color(PlannhColors.titleColor(data.getType())))
            .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN);

        topRow.child(
            new HeaderTextWidget(this, PlannhColors.titleColor(data.getType())).setScale(1)
                .background()
                .setTextColor(PlannhColors.NODE_TITLE_LINE.getColor()));

        topRow.child(new CloseButtonWidget(this));

        mainColumn.child(topRow)
            .child(new StepAreaWidget(this))
            .child(new AmountTextFieldWidget(this));

        child(mainColumn);
    }

    @Override
    protected Map<UUID, Step> getDefaultContainer() {
        return canvas.getGraph()
            .getSteps();
    }

    public int getWorldWidth() {
        return EST_W;
    }

    public int getWorldHeight() {
        return EST_H;
    }

    public int getOutputPortAt(final int localMx, final int localMy) {
        final int px = getWorldWidth() - PORT_SIZE;
        final int py = getWorldHeight() / 2 - PORT_HALF;
        if (localMx >= px && localMx < px + PORT_SIZE && localMy >= py && localMy < py + PORT_SIZE) return 0;
        return -1;
    }

    public int getInputPortAt(final int localMx, final int localMy) {
        final int py = getWorldHeight() / 2 - PORT_HALF;
        if (localMx >= 0 && localMx < PORT_SIZE && localMy >= py && localMy < py + PORT_SIZE) return 0;
        return -1;
    }

    @Override
    public void draw(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
        super.draw(context, widgetTheme);
        drawPorts();
    }

    private void drawPorts() {
        final boolean isFluid = !data.getOutputs()
            .isEmpty()
            && data.getOutputs()
                .getFirst()
                .getType() == RecipePropertyAPI.FLUID;

        // Output port (right side)
        {
            final int px = getWorldWidth() - PORT_SIZE;
            final int py = getWorldHeight() / 2 - PORT_HALF;
            if (isFluid) {
                GuiDraw.drawRect(px - 1, py - 1, PORT_SIZE + 2, PORT_SIZE + 2, PlannhColors.PIN_FLUID_OUT_H.getColor());
                GuiDraw.drawRect(px, py, PORT_SIZE, PORT_SIZE, PlannhColors.PIN_FLUID_OUT.getColor());
            } else {
                GuiDraw
                    .drawRect(px - 1, py - 1, PORT_SIZE + 2, PORT_SIZE + 2, PlannhColors.PIN_OUTPUT_HOVER.getColor());
                GuiDraw.drawRect(px, py, PORT_SIZE, PORT_SIZE, PlannhColors.PIN_OUTPUT.getColor());
            }
        }

        // Input port (left side)
        {
            final int py = getWorldHeight() / 2 - PORT_HALF;
            if (isFluid) {
                GuiDraw.drawRect(-1, py - 1, PORT_SIZE + 2, PORT_SIZE + 2, PlannhColors.PIN_FLUID_IN_H.getColor());
                GuiDraw.drawRect(0, py, PORT_SIZE, PORT_SIZE, PlannhColors.PIN_FLUID_IN.getColor());
            } else {
                GuiDraw.drawRect(-1, py - 1, PORT_SIZE + 2, PORT_SIZE + 2, PlannhColors.PIN_INPUT_HOVER.getColor());
                GuiDraw.drawRect(0, py, PORT_SIZE, PORT_SIZE, PlannhColors.PIN_INPUT.getColor());
            }
        }
    }
}
