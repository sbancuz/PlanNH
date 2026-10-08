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
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartTextWidget;
import com.sbancuz.plannh.gui.tooltips.TooltipBuilder;

class TargetFold extends NodeFold {

    private static final String LANG = "plannh.gui.node.target.";

    /** The two step buttons, square like the fold toggles they sit under. */
    private static final int STEP_BUTTON = 12;

    private BalanceMode savedMode;
    private Pin savedSelected;

    public TargetFold(final NodeWidget node) {
        super(
            node,
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
}
