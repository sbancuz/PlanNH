package com.sbancuz.plannh.gui.node;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.PlannhColors;
import com.sbancuz.plannh.gui.common.FlowchartWidget;

/** Pins this machine's count, or unpins it. */
public class MachineCountFixedButtonWidget extends ButtonWidget<MachineCountFixedButtonWidget> {

    private static final String LANG = "plannh.gui.node.fixed";

    private final FlowchartWidget<?, ?> parent;

    public MachineCountFixedButtonWidget(FlowchartWidget<?, ?> parent) {
        this.parent = parent;
        size(12);

        background(new Rectangle().color(PlannhColors.CHIP_BG.getColor()));
        hoverBackground(new Rectangle().color(Color.argb(255, 255, 255, 32)));

        tooltipBuilder(t -> {
            t.addLine(IKey.lang(LANG));
            t.addLine(IKey.lang(() -> LANG + (!node().isMachineCountFixed() ? ".pinned_hint" : ".unpinned_hint")));
        });
        overlay(
            IKey.lang(LANG + ".short")
                .color(
                    () -> node().isMachineCountFixed() ? PlannhColors.SETTING_ON.getColor()
                        : PlannhColors.SETTING_OFF.getColor()));
    }

    private Node node() {
        return (Node) parent.getData();
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        final CanvasWidget canvas = parent.getCanvas();
        if (!canvas.isMouseInsideCanvas()) return Result.SUCCESS;

        final Node node = node();
        PlanAPI.recordEdit(canvas.getGraph(), () -> node.setMachineCountFixed(!node.isMachineCountFixed()));

        canvas.getGraph()
            .bumpVersion();
        PlanAPI.save();
        return Result.SUCCESS;
    }
}
