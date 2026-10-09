package com.sbancuz.plannh.gui.summary;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.EnumValue;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.sbancuz.plannh.data.channels.ChannelProblem.Feed;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Summary;

/** On/off, the priority mode and the feed; each re-derives the rows. */
final class ChannelControlsRow extends ChannelRow {

    private final Summary data;

    ChannelControlsRow(final Summary data) {
        super(GuiAxis.X);
        this.data = data;

        final CycleButtonWidget enabled = button(2).stateOverlay(false, IKey.lang(KEY + "enabled.off"))
            .stateOverlay(true, IKey.lang(KEY + "enabled.on"))
            .value(new BoolValue.Dynamic(data::isChannelsEnabled, val -> {
                data.setChannelsEnabled(val);
                refresh();
            }));
        for (int state = 0; state < 2; state++) enabled.tooltip(state, ChannelControlsRow::enabledTooltip);

        final CycleButtonWidget mode = button(2).stateOverlay(false, IKey.lang(KEY + "mode.separate.button"))
            .stateOverlay(true, IKey.lang(KEY + "mode.priority.button"))
            .value(new BoolValue.Dynamic(data::isChannelPriority, val -> {
                data.setChannelPriority(val);
                refresh();
            }));
        // A cycle button shows its current state's tooltip, so each state describes both
        mode.tooltip(0, t -> modeTooltip(t, false));
        mode.tooltip(1, t -> modeTooltip(t, true));

        final CycleButtonWidget feed = button(Feed.values().length)
            .value(new EnumValue.Dynamic<>(Feed.class, data::getChannelFeed, val -> {
                data.setChannelFeed(val);
                refresh();
            }));
        for (final Feed f : Feed.values()) {
            feed.stateOverlay(f, IKey.lang(KEY + feedKey(f) + ".button"));
            feed.tooltip(f.ordinal(), t -> feedTooltip(t, f));
        }

        fullWidth().coverChildrenHeight()
            .paddingLeft(SummaryBody.TEXT_X)
            .childPadding(GAP)
            .child(enabled)
            .child(mode.setEnabledIf(_ -> data.isChannelsEnabled()))
            .child(feed.setEnabledIf(_ -> data.isChannelsEnabled()));
    }

    private static CycleButtonWidget button(final int states) {
        return new CycleButtonWidget().stateCount(states)
            .expanded()
            .height(SummaryHeader.HEADER_H);
    }

    private void refresh() {
        data.recompute(Plan.getActiveGraph());
    }

    private static String feedKey(final Feed feed) {
        return "feed." + switch (feed) {
            case PASSIVE -> "passive";
            case BATCH -> "batch";
        };
    }

    private static void enabledTooltip(final RichTooltip t) {
        title(t, tr("enabled.title"));
        body(t, tr("enabled.help"));
        footer(t, StatCollector.translateToLocal("plannh.summary.mode.switch_hint"));
    }

    private static void modeTooltip(final RichTooltip t, final boolean priority) {
        title(t, tr("mode.title"));
        gap(t);
        option(t, tr("mode.separate.name"), tr("mode.separate.help"), !priority);
        gap(t);
        option(t, tr("mode.priority.name"), tr("mode.priority.help"), priority);
        gap(t);
        footer(t, StatCollector.translateToLocal("plannh.summary.mode.switch_hint"));
    }

    private static void feedTooltip(final RichTooltip t, final Feed current) {
        title(t, tr("feed.title"));
        for (final Feed f : Feed.values()) {
            gap(t);
            option(t, tr(feedKey(f)), tr(feedKey(f) + ".help"), f == current);
        }
        gap(t);
        footer(t, StatCollector.translateToLocal("plannh.summary.mode.switch_hint"));
    }
}
