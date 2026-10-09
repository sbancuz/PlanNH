package com.sbancuz.plannh.data.setting;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.sbancuz.plannh.data.flowchart.balancer.Balancer;
import com.sbancuz.plannh.gui.GuiHelper;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartTextWidget;
import com.sbancuz.plannh.gui.node.NodeWidget;
import com.sbancuz.plannh.gui.tooltips.TooltipBuilder;
import com.sbancuz.plannh.gui.tooltips.TooltipTheme;

/**
 * The copies target: how many copies of this machine the chart runs.
 *
 * <p>
 * A value of its own, not {@link Settings#MACHINES}. That setting is how many machines this recipe
 * runs on at once; this is how many of them the chart runs, and the two are different numbers that
 * happen to be counted in the same unit.
 */
public final class CopyCountTarget implements TargetKind {

    private static final String LANG = "plannh.gui.node.target.";

    private static final int MIN = 1;
    private static final int MAX = 4096;

    /** A copy is wired in or absent, so there is no count below one. */
    private int copies = MIN;

    public int copies() {
        return copies;
    }

    /** Clamped, because the field is only one way in: a save or a fixture can be any number. */
    public void setCopies(final int copies) {
        this.copies = Math.clamp(copies, MIN, MAX);
    }

    @Override
    public IWidget widget(final NodeWidget parent, final Consumer<Runnable> edit) {
        final IWidget label = new FlowchartTextWidget(IKey.lang(LANG + "copies"), parent).tooltipDynamic(
            t -> t.addLine(IKey.lang(LANG + "solved"))
                .addLine(solved(parent)));
        final FlowchartFlow flow = FlowchartFlow.row(parent);

        flow.name("target.copies")
            .fullWidth()
            .coverChildrenHeight()
            .childPadding(2)
            .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
            .child(label)
            .child(
                new TextFieldWidget().name("target.copies.field")
                    .width(IntegerSettingDef.fieldWidth(MIN, MAX))
                    .value(new IntValue.Dynamic(() -> copies, typed -> edit.accept(() -> copies = typed)))
                    .numbersInt(MIN, MAX)
                    .formatAsInteger(true));

        return flow;
    }

    /**
     * The copies this target states, and what the balance would have run without it - so a pin doing
     * nothing is visible rather than merely declared.
     *
     * <p>
     * Nothing at all when the machine is unplanned: there is no answer for the target to be measured
     * against, and stating the number anyway would read as though the balance had asked for it.
     */
    @Override
    public void tooltip(final TooltipBuilder out, final NodeWidget parent) {
        final IKey solved = solved(parent);
        if (solved == null) return;

        out.detail(StatCollector.translateToLocal(LANG + "copies"), TooltipTheme.Role.TUNABLE, String.valueOf(copies));
        out.detail(StatCollector.translateToLocal(LANG + "solved"), TooltipTheme.Role.IDENTITY, solved.getFormatted());
    }

    /** What the chart would otherwise have run, or null when it has no answer. */
    private static @Nullable IKey solved(final NodeWidget parent) {
        final Balancer.NodeBalance balance = parent.getCanvas()
            .getGraph()
            .balance()
            .nodeBalances()
            .get(
                parent.getData()
                    .getId());
        if (balance == null || balance.operations() <= 0) return null;
        return IKey.str("×" + GuiHelper.formatCount(balance.operations()));
    }
}
