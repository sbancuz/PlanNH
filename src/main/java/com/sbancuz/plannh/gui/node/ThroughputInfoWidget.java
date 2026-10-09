package com.sbancuz.plannh.gui.node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.util.StatCollector;

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
import com.sbancuz.plannh.data.flowchart.balancer.Pin;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.properties.SummaryProperty;
import com.sbancuz.plannh.data.setting.SettingDef;
import com.sbancuz.plannh.data.setting.TargetKind;
import com.sbancuz.plannh.gui.GuiHelper;
import com.sbancuz.plannh.gui.GuiHelper.RateUnit;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartTextWidget;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.common.IFlowchartDraggable;
import com.sbancuz.plannh.gui.tooltips.TooltipBuilder;
import com.sbancuz.plannh.gui.tooltips.TooltipTheme;

public class ThroughputInfoWidget extends ParentWidget<ThroughputInfoWidget> implements IFlowchartDraggable {

    private static final String LANG = "plannh.gui.node.throughput.";
    private static final String TARGET = "plannh.gui.node.target.";

    private final NodeWidget parent;
    private final Flow lines;

    private Flow row;

    private double lastOperations = Double.NaN;
    private int lastDuration = Integer.MIN_VALUE;

    private long lastVersion = Long.MIN_VALUE;
    private boolean lastShift;
    private RateUnit lastUnit;

    public ThroughputInfoWidget(NodeWidget parent) {
        this.parent = parent;
        name("node.info");
        fullWidth();
        coverChildrenHeight();

        lines = FlowchartFlow.col(parent)
            .name("node.info.lines")
            .fullWidth()
            .coverChildrenHeight();
        child(lines);

        rebuild();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        invalidateTooltip();
        rebuild();
    }

    @Override
    public FlowchartWidget<?, ?> getFlowchartParent() {
        return parent;
    }

    private void invalidateTooltip() {
        final long version = parent.getCanvas()
            .getGraph()
            .getVersion();
        final boolean shift = GuiHelper.shiftHeld();
        final RateUnit unit = rateUnit();
        if (version == lastVersion && shift == lastShift && unit == lastUnit) return;
        lastVersion = version;
        lastShift = shift;
        lastUnit = unit;
        if (row != null) row.markTooltipDirty();
    }

    private void rebuild() {
        final Node node = parent.getData();
        final Balancer.NodeBalance balance = parent.balance();
        final int duration = balance == null ? durationTicks(node) : balance.durationPerOp();
        final double operations = balance == null ? -1 : balance.operations();

        if (operations == lastOperations && duration == lastDuration) return;
        lastOperations = operations;
        lastDuration = duration;

        final IKey copies;
        if (operations < 0) copies = IKey.lang(LANG + "unbalanced");
        else if (operations <= 0) copies = IKey.lang(LANG + "unplanned");
        else copies = IKey.comp(IKey.lang(LANG + "copies_needed"), TooltipBuilder.multiple(operations));

        lines.removeAll();

        // What the balance settled on, and how long a craft takes. Which target asked for it, and
        // whether the rows below are showing, belong to the target list - this row only states the
        // two numbers, and carries the tooltip that explains them.
        row = FlowchartFlow.row(parent)
            .name("throughput")
            .fullWidth()
            .coverChildrenHeight()
            .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
            .childPadding(2)
            .tooltipBuilder(this::writeTooltip)
            .child(new FlowchartTextWidget(copies, parent).name("node.info.copies"))
            .child(
                new FlowchartTextWidget(IKey.lang(LANG + "duration", GuiHelper.formatDuration(duration)), parent)
                    .name("node.info.duration")
                    .textAlign(Alignment.CenterRight));
        lines.child(row);

        scheduleResize();
    }

    private void writeTooltip(final RichTooltip tooltip) {
        final Node node = parent.getData();
        final Balancer.NodeBalance balance = parent.balance();
        final boolean balanced = balance != null && balance.durationPerOp() > 0;
        final RateUnit unit = rateUnit();
        final int ticks = balanced ? balance.durationPerOp() : durationTicks(node);

        final TooltipBuilder out = TooltipBuilder.create(tooltip);

        if (ticks > 0) out.duration(RecipePropertyAPI.DURATION_TICKS.displayName(), ticks, unit, GuiHelper.shiftHeld());
        else out.langRow(LANG + "unbalanced");

        out.group(
            "plannh.summary.title.outputs",
            rows -> { if (balanced) ports(rows, node.getOutputs(), balance.effectiveOutputs(), ticks, unit); });
        out.group(
            "plannh.summary.title.inputs",
            rows -> { if (balanced) ports(rows, node.getInputs(), balance.effectiveInputs(), ticks, unit); });
        out.group("plannh.summary.title.properties", rows -> properties(rows, node));

        writeTarget(out);

        // Shift trades the hint that says what shift does for the thing it does, which is the only
        // reason to have a hint.
        if (GuiHelper.shiftHeld()) writeSettings(out);
        else out.separator()
            .langRow(LANG + "shift_hint");

        out.flush();
    }

    private void writeTarget(final TooltipBuilder out) {
        final List<Pin> held = parent.balanceMode()
            .pins()
            .stream()
            .filter(pin -> pin.hasWidget && pin != Pin.NONE)
            .toList();
        // A mode that pins nothing has nothing to choose between, so there is no fold to describe.
        if (held.isEmpty()) return;

        final MachineConfig config = parent.getData()
            .getMachineConfig();
        final Pin selected = config.getTargetKind();
        final TargetKind kind = TargetKind.of(config, selected);

        out.group(TARGET + "title", rows -> {
            for (final Pin pin : held) {
                rows.marker(selected == pin ? ">" : " ", StatCollector.translateToLocal(pin.key()), selected == pin);
            }
            if (selected == Pin.NONE) rows.langRow(TARGET + "none_hint");
            else if (kind != null) kind.tooltip(rows, parent);
        });
    }

    /** Every setting the machine has a row for, as the machine currently reads it. */
    @SuppressWarnings("unchecked")
    private void writeSettings(final TooltipBuilder out) {
        final Node node = parent.getData();
        final MachineConfig config = node.getMachineConfig();

        out.group(LANG + "settings", rows -> {
            for (final SettingDef<?> def : visible(config, node)) {
                final Object value = config.get(def);
                if (value != null) ((SettingDef<Object>) def).tooltip(value)
                    .accept(rows);
            }
        });
    }

    private static List<SettingDef<?>> visible(final MachineConfig config, final Node node) {
        return config.getProfile()
            .visibleSettings(new RecipeContext(node.getProperties()), config)
            .<SettingDef<?>>map(def -> def)
            .toList();
    }

    /**
     * One row per port actually moving: a port this craft does not move, or moves at zero, is not a
     * row.
     */
    private static void ports(final TooltipBuilder out, final List<Port<?>> ports, final Map<Integer, Float> perCycle,
        final int duration, final RateUnit unit) {
        for (int i = 0; i < ports.size(); i++) {
            final Float rate = perCycle.get(i);
            if (rate == null || rate <= 0) continue;
            final Port<?> port = ports.get(i);
            out.detail(
                port.getDisplayName(),
                TooltipTheme.Role.RATE,
                TooltipBuilder.rate(
                    port.getType()
                        .formatAmount(GuiHelper.rate(rate, duration, unit)),
                    unit));
        }
    }

    /** Every summary property this machine actually spends, sorted so the list does not jump around. */
    @SuppressWarnings("unchecked")
    private static void properties(final TooltipBuilder out, final Node node) {
        final List<SummaryProperty> found = new ArrayList<>();
        for (final Map.Entry<RecipeProperty<?>, Object> entry : node.getProperties()
            .entrySet()) {
            if (!(entry.getKey() instanceof final SummaryProperty prop)) continue;
            if (!(entry.getValue() instanceof final Number num) || num.floatValue() == 0) continue;
            found.add(prop);
        }
        found.sort(Comparator.comparing(prop -> prop.formatDisplayName(prop.getDefaultValue())));

        for (final SummaryProperty prop : found) {
            final String name = prop.formatDisplayName(prop.getDefaultValue());
            final float value = ((Number) node.getProperties()
                .get(prop)).floatValue();
            // A property named per tick is energy, and the pack marks energy wherever it turns up.
            out.detail(name, name.endsWith("/t") ? TooltipTheme.Role.POWER : TooltipTheme.Role.BODY,
                prop.formatAmount(value));
        }
    }

    /** The recipe's own craft time, which a machine knows before it has ever been solved. */
    private static int durationTicks(final Node node) {
        final Object raw = node.getProperties()
            .get(RecipePropertyAPI.DURATION_TICKS);
        return raw instanceof final Number n ? n.intValue() : 0;
    }

    private static RateUnit rateUnit() {
        return Plan.getInstance()
            .getSummary()
            .getRateUnit();
    }

}
