package com.sbancuz.plannh.gui.node;

import java.util.Set;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceMode;
import com.sbancuz.plannh.data.flowchart.balancer.Pin;
import com.sbancuz.plannh.data.setting.TargetKind;
import com.sbancuz.plannh.gui.PlannhColors;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartTextWidget;
import com.sbancuz.plannh.gui.tooltips.TooltipBuilder;

/**
 * The balancer-target fold: the rows that set whatever this machine is currently held to.
 *
 * <p>
 * Which kind it shows is decided by the buttons on the throughput row, so the fold has nothing to
 * decide for itself - it is only asked whether to be showing, by the toggle on that same row.
 */
class TargetList extends NodeFoldList<TargetList> {

    private static final String LANG = "plannh.gui.node.target.";

    /** How many rows to show before the screen bound takes over as the limit. */
    private static final int VISIBLE_ROWS = 7;

    /** The two step buttons, square like the fold toggles they sit under. */
    private static final int STEP_BUTTON = 12;

    private BalanceMode savedMode;
    private Pin savedSelected;

    public TargetList(final NodeWidget node) {
        super(
            node,
            VISIBLE_ROWS,
            () -> node.getData()
                .isTargetOpen());

        savedMode = balancerMode();
        savedSelected = selected();

        onUpdateListener(w -> {
            if (savedSelected != selected() || savedMode != balancerMode()) {
                savedMode = balancerMode();
                savedSelected = selected();
                markStale();
            }
        }, true);
    }

    private BalanceMode balancerMode() {
        return node.getCanvas()
            .getGraph()
            .getBalanceMode();
    }

    private MachineConfig config() {
        return node.getData()
            .getMachineConfig();
    }

    private Pin selected() {
        return config().getTargetKind();
    }

    @Override
    protected void rebuild() {
        removeAll();
        child(selector());
        final Set<Pin> pages = balancerMode().pins();
        if (pages.isEmpty()) child(hint(LANG + "unsupported", balancerMode().displayName()));
        else child(page());
        scheduleResize();
    }

    private IWidget selector() {
        final Flow row = FlowchartFlow.row(node)
            .fullWidth()
            .coverChildrenHeight()
            .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
            .childPadding(2);

        row.child(new FlowchartTextWidget(IKey.lang(LANG + "label"), node))
            .child(
                FlowchartFlow.row(node)
                    .coverChildren()
                    .childPadding(2)
                    .child(step(-1))
                    .child(new FlowchartTextWidget(IKey.lang(selected().key()), node))
                    .child(step(1)));
        return row;
    }

    private IWidget step(final int delta) {
        return new ButtonWidget<>().size(STEP_BUTTON)
            .overlay(IKey.str(delta < 0 ? "<" : ">"))
            .onMousePressed(clicked -> {
                if (clicked != 0) return false;
                commitEdit(() -> config().setTargetKind(nextPin(delta)));
                return true;
            })
            .tooltipBuilder(
                tooltip -> TooltipBuilder.create(tooltip)
                    .langRows(LANG + (delta < 0 ? "prev" : "next"))
                    .flush());
    }

    /** The next pin this balance mode honours, stepping only over the ones with a widget to set them. */
    private Pin nextPin(final int delta) {
        final Set<Pin> pins = balancerMode().pins();
        if (pins.isEmpty()) return Pin.NONE;

        Pin next = selected();
        do {
            next = Pin.VALUES[Math.floorMod(next.ordinal() + delta, Pin.VALUES.length)];
        } while (!next.hasWidget || !pins.contains(next));

        return next;
    }

    private IWidget page() {
        final Pin pin = selected();
        final TargetKind kind = TargetKind.of(config(), pin);
        if (kind == null) return hint(LANG + "extent");

        return kind.widget(node, change -> commitEdit(() -> {
            change.run();
            config().setTargetKind(pin);
        }));
    }

    private Flow hint(final String key, final Object... args) {
        return FlowchartFlow.row(node)
            .fullWidth()
            .coverChildrenHeight()
            .padding(2)
            .child(
                new FlowchartTextWidget(
                    IKey.lang(key, args)
                        .color(PlannhColors.TEXT_DIM.getColor()),
                    // IDK how to get it here
                    node).maxWidth(176 - 2 * 5 - 4));
    }
}
