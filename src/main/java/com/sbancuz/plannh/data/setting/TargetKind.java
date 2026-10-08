package com.sbancuz.plannh.data.setting;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.flowchart.balancer.Pin;
import com.sbancuz.plannh.gui.node.NodeWidget;
import com.sbancuz.plannh.gui.tooltips.TooltipBuilder;

public interface TargetKind {

    IWidget widget(NodeWidget parent, Consumer<Runnable> edit);

    void tooltip(TooltipBuilder out, NodeWidget parent);

    /** Null for a kind the chart cannot edit: {@link Pin#EXTENT} arrives per solve, not on a machine. */
    static @Nullable TargetKind of(final MachineConfig config, final Pin pin) {
        return switch (pin) {
            case FIXED_COPIES -> config.getCopies();
            case TARGET_RATE -> config.getRates();
            default -> null;
        };
    }
}
