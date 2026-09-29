package com.sbancuz.plannh.gui.node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;
import com.sbancuz.plannh.data.properties.SummaryProperty;
import com.sbancuz.plannh.data.setting.SettingDef;
import com.sbancuz.plannh.gui.GuiHelper;
import com.sbancuz.plannh.gui.GuiHelper.RateUnit;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartTextWidget;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.common.IFlowchartDraggable;
import com.sbancuz.plannh.gui.common.TooltipStyle;

public class ThroughputInfoWidget extends ParentWidget<ThroughputInfoWidget> implements IFlowchartDraggable {

    private static final String LANG = "plannh.gui.node.throughput.";

    private final NodeWidget parent;
    private final Flow lines;

    private double lastOperations = Double.NaN;
    private int lastDuration = Integer.MIN_VALUE;

    public ThroughputInfoWidget(NodeWidget parent) {
        this.parent = parent;
        fullWidth();
        coverChildrenHeight();

        lines = FlowchartFlow.col(parent)
            .fullWidth()
            .coverChildrenHeight();
        child(lines);

        rebuild();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        rebuild();
    }

    @Override
    public FlowchartWidget<?, ?> getFlowchartParent() {
        return parent;
    }

    private void rebuild() {
        final Node node = parent.getData();
        final Balancer.NodeBalance nb = balance();
        final int duration = nb == null ? durationTicks(node) : nb.durationPerOp();
        final double operations = nb == null ? -1 : nb.operations();

        if (operations == lastOperations && duration == lastDuration) return;
        lastOperations = operations;
        lastDuration = duration;

        final IKey count;
        if (operations < 0) count = IKey.lang(LANG + "unbalanced");
        else if (operations <= 0) count = IKey.lang(LANG + "unplanned");
        else count = IKey.lang(LANG + "ops", GuiHelper.formatCount(operations));

        lines.removeAll();
        lines.child(
            FlowchartFlow.row(parent)
                .fullWidth()
                .coverChildrenHeight()
                .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                .tooltipBuilder(this::rates)
                .tooltipAutoUpdate(true)
                .child(
                    FlowchartFlow.row(parent)
                        .coverChildrenHeight()
                        .childPadding(2)
                        .child(new MachineCountFixedButtonWidget(parent))
                        .child(new FlowchartTextWidget(count, parent)))
                .child(
                    new FlowchartTextWidget(IKey.lang(LANG + "duration", GuiHelper.formatDuration(duration)), parent)));

        scheduleResize();
    }

    private Balancer.NodeBalance balance() {
        return parent.getCanvas()
            .getGraph()
            .balance()
            .nodeBalances()
            .get(
                parent.getData()
                    .getId());
    }

    private void rates(final RichTooltip t) {
        final Node node = parent.getData();
        final Balancer.NodeBalance nb = balance();
        final boolean balanced = nb != null && nb.durationPerOp() > 0;
        final int ticks = balanced ? nb.durationPerOp() : durationTicks(node);
        final RateUnit unit = Plan.getInstance()
            .getSummary()
            .getRateUnit();

        if (ticks > 0) t.addLine(
            IKey.str(
                TooltipStyle.plain(
                    RecipePropertyAPI.DURATION_TICKS.displayName(),
                    TooltipStyle.IDENTITY,
                    duration(ticks, unit))));
        else t.addLine(
            IKey.lang(LANG + "unbalanced")
                .style(TooltipStyle.BODY));

        if (balanced) {
            final float cycleSeconds = nb.durationPerOp() / (float) GuiHelper.TICKS_PER_SECOND;
            group(
                t,
                "plannh.summary.title.outputs",
                portLines(node.getOutputs(), nb.effectiveOutputs(), cycleSeconds, unit));
            group(
                t,
                "plannh.summary.title.inputs",
                portLines(node.getInputs(), nb.effectiveInputs(), cycleSeconds, unit));
        }
        group(t, "plannh.summary.title.properties", costLines(node));

        if (isShiftHeld()) {
            settings(t, node);
        } else {
            t.addLine(IKey.str(TooltipStyle.rule()));
            t.addLine(IKey.lang(LANG + "shift_hint"));
        }
    }

    private static String duration(final int ticks, final RateUnit unit) {
        final double seconds = ticks / (double) GuiHelper.TICKS_PER_SECOND;
        if (isShiftHeld()) {
            final StringBuilder all = new StringBuilder();
            for (final RateUnit each : RateUnit.VALUES) {
                if (!each.duration) continue;
                all.append(number(seconds / each.secondsPerUnit))
                    .append(' ')
                    .append(lang(each.langKey))
                    .append(TooltipStyle.BODY)
                    .append(", ");
            }
            return all.append(ticks)
                .append(" t")
                .toString();
        }
        final String in = number(seconds / unit.secondsPerUnit) + " " + lang(unit.langKey);
        if (!unit.duration) return in;
        return in + TooltipStyle.BODY + " (" + ticks + " t" + TooltipStyle.BODY + ")";
    }

    private static List<String> portLines(final List<Port<?>> ports, final Map<Integer, Float> perCycle,
        final float cycleSeconds, final RateUnit unit) {
        final List<String> items = new ArrayList<>();
        for (int i = 0; i < ports.size(); i++) {
            final Float rate = perCycle.get(i);
            if (rate == null || rate <= 0) continue;
            final Port<?> port = ports.get(i);
            final float amount = rate / cycleSeconds / (float) unit.secondsPerUnit;
            items.add(
                TooltipStyle.entry(
                    port.getDisplayName(),
                    TooltipStyle.RATE,
                    port.getType()
                        .formatAmount(amount) + lang(unit.suffixKey())));
        }
        return items;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<String> costLines(final Node node) {
        final List<SummaryProperty> found = new ArrayList<>();
        for (final var entry : node.getProperties()
            .entrySet()) {
            if (!(entry.getKey() instanceof final SummaryProperty prop)) continue;
            if (!(entry.getValue() instanceof final Number num) || num.floatValue() == 0) continue;
            found.add(prop);
        }
        if (found.isEmpty()) return List.of();

        found.sort(Comparator.comparing(prop -> prop.formatDisplayName(prop.getDefaultValue())));
        final List<String> items = new ArrayList<>();
        for (final SummaryProperty prop : found) {
            final String name = prop.formatDisplayName(prop.getDefaultValue());
            final float value = ((Number) node.getProperties()
                .get(prop)).floatValue();
            items.add(
                TooltipStyle.entry(name, name.endsWith("/t") ? TooltipStyle.POWER : TooltipStyle.BODY,
                    prop.formatAmount(value)));
        }
        return items;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void settings(final RichTooltip t, final Node node) {
        final MachineConfig config = node.getMachineConfig();
        final List<String> items = new ArrayList<>();
        for (final SettingDef def : config.getProfile()
            .visibleSettings(new RecipeContext(node.getProperties()), config)
            .toList()) {
            final Object value = config.get(def);
            if (value == null) continue;
            final String item = def.tooltip(value);
            if (item != null) items.add(item);
        }
        group(t, LANG + "settings", items);
    }

    private static void group(final RichTooltip t, final String headerKey, final List<String> items) {
        if (items.isEmpty()) return;
        t.addLine(IKey.str(TooltipStyle.rule()));
        t.addLine(IKey.str(TooltipStyle.header(headerKey)));
        for (final String item : items) t.addLine(IKey.str(item));
    }

    private static String number(final double value) {
        return GuiHelper.trimTrailingZeros(String.format(Locale.ROOT, "%.2f", value));
    }

    private static String lang(final String key) {
        return StatCollector.translateToLocal(key);
    }

    private static boolean isShiftHeld() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static int durationTicks(final Node node) {
        final Object raw = node.getProperties()
            .get(RecipePropertyAPI.DURATION_TICKS);
        return raw instanceof final Number n ? n.intValue() : 0;
    }
}
