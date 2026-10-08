package com.sbancuz.plannh.data.setting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.gui.GuiHelper;
import com.sbancuz.plannh.gui.GuiHelper.RateUnit;
import com.sbancuz.plannh.gui.PlannhColors;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartTextWidget;
import com.sbancuz.plannh.gui.node.NodeWidget;
import com.sbancuz.plannh.gui.tooltips.TooltipBuilder;
import com.sbancuz.plannh.gui.tooltips.TooltipTheme;

/**
 * The output-rate target: how much of each output the chart should produce, per second.
 *
 * <p>
 * Keyed by output port index. A row left at zero is no target, so the field is the pin.
 */
public final class OutputRateTarget implements TargetKind {

    private static final String LANG = "plannh.gui.node.target.";

    private static final int ROW_GAP = 2;
    private static final double MAX_RATE = 1_000_000;

    /** Rate per output port index, in ingredient units per second. */
    private final Map<Integer, Double> rates = new LinkedHashMap<>();

    public Map<Integer, Double> rates() {
        return rates;
    }

    public double rate(final int port) {
        final Double rate = rates.get(port);
        return rate == null ? 0 : rate;
    }

    public Map<Integer, Double> liveTargets(final double[] outQty) {
        final Map<Integer, Double> live = new LinkedHashMap<>();
        for (final Map.Entry<Integer, Double> t : rates.entrySet()) {
            if (t.getValue() == null || t.getValue() <= 0) continue;
            final int i = t.getKey();
            if (i < 0 || i >= outQty.length || outQty[i] <= 0) continue;
            live.put(i, t.getValue());
        }
        return live;
    }

    /**
     * The extent these targets imply: rate over per-craft quantity, largest target winning - parallel
     * outputs share one extent, so only the tightest can be hit exactly.
     */
    public double extent(final double[] outQty) {
        double extent = 0;
        for (final Map.Entry<Integer, Double> t : liveTargets(outQty).entrySet()) {
            extent = Math.max(extent, t.getValue() / outQty[t.getKey()]);
        }
        return extent;
    }

    @Override
    public IWidget widget(final NodeWidget parent, final Consumer<Runnable> edit) {
        final Flow column = FlowchartFlow.col(parent)
            .name("target.rates")
            .fullWidth()
            .coverChildrenHeight()
            .childPadding(ROW_GAP);

        int rows = 0;
        final List<Port<?>> outputs = parent.getData()
            .getOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            final Port<?> port = outputs.get(i);
            if (!port.hasVisibleAmount()) continue;
            column.child(row(parent, port, i, edit));
            rows++;
        }
        if (rows == 0) return FlowchartFlow.row(parent)
            .name("target.rates.hint")
            .fullWidth()
            .coverChildrenHeight()
            .padding(2)
            .child(
                new FlowchartTextWidget(
                    IKey.lang(LANG + "no_outputs", new Object[] {})
                        .color(PlannhColors.TEXT_DIM.getColor()),
                    parent));
        return column;
    }

    /**
     * Every target still in force, one row each.
     */
    @Override
    public void tooltip(final TooltipBuilder out, final NodeWidget parent) {
        final List<Port<?>> outputs = parent.getData()
            .getOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            final double target = rate(i);
            if (target <= 0) continue;
            final Port<?> port = outputs.get(i);
            out.detail(
                port.getDisplayName(),
                TooltipTheme.Role.RATE,
                TooltipBuilder.rate(
                    port.getType()
                        .formatAmount((float) target),
                    RateUnit.SECONDS));
        }
    }

    private Flow row(final NodeWidget parent, final Port<?> port, final int index, final Consumer<Runnable> edit) {
        final IWidget label = new FlowchartTextWidget(
            IKey.str(port.getDisplayName())
                .color(rate(index) > 0 ? PlannhColors.SETTING_ON.getColor() : PlannhColors.TEXT_DIM.getColor()),
            parent).widthRel(2 / 3f);

        final FlowchartFlow flow = FlowchartFlow.row(parent);
        flow.name("target.rates.row")
            .fullWidth()
            .coverChildrenHeight()
            .childPadding(2)
            .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
            .child(label)
            .child(
                new TextFieldWidget().name("target.rates.field")
                    .width(fieldWidth())
                    .value(new DoubleValue.Dynamic(() -> rate(index), typed -> edit.accept(() -> {
                        if (typed <= 0) rates.remove(index);
                        else rates.put(index, typed);
                    })))
                    .numbersDouble(0, MAX_RATE));
        return flow;
    }

    private static int fieldWidth() {
        return IntegerSettingDef.fieldWidth(GuiHelper.formatRate((float) MAX_RATE));
    }
}
