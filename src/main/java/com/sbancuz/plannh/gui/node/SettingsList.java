package com.sbancuz.plannh.gui.node;

import com.cleanroommc.modularui.utils.Alignment;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartTextWidget;

class SettingsList extends NodeFoldList<SettingsList> {

    private static final int VISIBLE_ROWS = 8;

    private final Node data;
    private final MachineConfig config;
    private MachineProfile savedProfile;

    public SettingsList(final NodeWidget node) {
        super(
            node,
            VISIBLE_ROWS,
            () -> node.getData()
                .isSettingsOpen());

        data = node.getData();
        config = data.getMachineConfig();
        savedProfile = config.getProfile();

        // Only the profile changes which settings there are to show. The pinned target used to be
        // watched here as well, back when the machine setting was hidden behind a pin; it is a
        // different number now and hiding one behind the other made no sense.
        onUpdateListener(w -> {
            if (savedProfile != config.getProfile()) {
                savedProfile = config.getProfile();
                markStale();
            }
        }, true);
    }

    @Override
    protected void rebuild() {
        removeAll();

        final MachineConfig config = data.getMachineConfig();
        config.getProfile()
            .visibleSettings(new RecipeContext(data.getProperties()), config)
            .forEach(
                def -> child(
                    FlowchartFlow.row(node)
                        .fullWidth()
                        .marginBottom(ROW_GAP)
                        .coverChildrenHeight()
                        .childPadding(2)
                        .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                        .child(new FlowchartTextWidget(def.getLabel(), node))
                        .child(def.settingsWidget(data.getMachineConfig(), change -> {
                            commitEdit(change);
                            markStale();
                        }))));

        scheduleResize();
    }

}
