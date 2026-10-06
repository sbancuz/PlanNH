package com.sbancuz.plannh.gui.node;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.MachineConfig;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.properties.PropertyProvider;
import com.sbancuz.plannh.data.setting.SettingDef;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.ClampedListWidget;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartTextWidget;

class SettingsList extends ClampedListWidget<IWidget, SettingsList> {

    /** How many rows the list is happy to show before the screen bound takes over as the limit. */
    private static final int VISIBLE_ROWS = 8;

    private static final int ROW_GAP = 4;
    private static final int SCROLLBAR_GAP = 4;

    private final NodeWidget node;
    private boolean dirty = true;
    private MachineProfile builtFor;
    private PropertyProvider builtWith;
    private int tallestRow;

    SettingsList(final NodeWidget node) {
        super();
        this.node = node;
        this.builtFor = profile();
        this.builtWith = node.getData()
            .getExtractor();

        // Maybe put a nice looking shadow here
        showScrollShadows(false);

        fullWidth().crossAxisAlignment(Alignment.CrossAxis.START)
            .paddingRight(SCROLLBAR_GAP)
            .scrollDirection(new VerticalScrollData())
            .setEnabledIf(
                _ -> node.getData()
                    .isSettingsOpen())
            // Unmeasured yet: do not cap, rather than cap to nothing for a frame.
            .maxSize(() -> tallestRow == 0 ? Integer.MAX_VALUE : VISIBLE_ROWS * tallestRow);
        rebuild();
        this.dirty = false;
    }

    @Override
    public boolean postLayoutWidgets() {
        final boolean done = super.postLayoutWidgets();
        int tallest = 0;
        for (final IWidget child : getChildren()) {
            tallest = Math.max(
                tallest,
                child.getArea()
                    .getSize(GuiAxis.Y)
                    + child.getArea()
                        .getMargin()
                        .getTotal(GuiAxis.Y));
        }
        if (tallest != tallestRow) {
            tallestRow = tallest;
            scheduleResize();
        }
        return done;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!dirty && builtFor == profile()
            && builtWith == node.getData()
                .getExtractor())
            return;
        // A number field commits on focus loss and on every scroll notch, and a rebuild destroys the
        // field - so a list that still holds focus waits, or the next notch lands nowhere.
        if (holdsFocus()) return;
        dirty = false;
        builtFor = profile();
        builtWith = node.getData()
            .getExtractor();
        rebuild();
    }

    @Override
    public boolean canClickThrough() {
        return true;
    }

    private boolean holdsFocus() {
        final LocatedWidget focused = getContext().getFocusedWidget();
        for (IWidget w = focused == null ? null : focused.getElement(); w != null; w = w.getParent()) {
            if (w == this) return true;
        }
        return false;
    }

    /** The rows are a function of the profile and the extracted recipe, so a change in either is a rebuild. */
    private MachineProfile profile() {
        return node.getData()
            .getMachineConfig()
            .getProfile();
    }

    private void rebuild() {
        removeAll();
        final MachineConfig config = node.getData()
            .getMachineConfig();
        config.getProfile()
            .visibleSettings(
                new RecipeContext(
                    node.getData()
                        .getProperties()),
                config)
            .forEach(def -> child(row(def)));
        scheduleResize();
    }

    private Flow row(final SettingDef<?> def) {
        return FlowchartFlow.row(node)
            .fullWidth()
            .coverChildrenHeight()
            .marginBottom(ROW_GAP)
            .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
            .child(new FlowchartTextWidget(def.getLabel(), node))
            .child(settingsWidget(def));
    }

    private IWidget settingsWidget(final SettingDef<?> def) {
        return def.settingsWidget(
            node.getData()
                .getMachineConfig(),
            this::applyEdit);
    }

    private void applyEdit(final Runnable change) {
        final CanvasWidget canvas = node.getCanvas();
        PlanAPI.recordEdit(canvas.getGraph(), change);
        canvas.getGraph()
            .bumpVersion();
        PlanAPI.save();
        dirty = true;
    }
}
