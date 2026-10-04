package com.sbancuz.plannh.gui.components;

import javax.annotation.Nonnull;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.menu.Menu;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.ChartMinimums;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.gui.PlannhColors;

/**
 * The active chart's floors: the settings an untouched node opens on.
 *
 * <p>
 * A chart is a factory at one point in a world's progression, so which coils it can build is set once per chart. A
 * floor can still be overridden on a node's row, and a node whose recipe requires more than the floor is raised.
 *
 * <p>
 * The rows come from {@link ChartMinimums}, which the installed providers fill. No mod is named in this class:
 * GregTech is a compile-only dependency, so a GregTech reference here would make the flowchart screen fail to open on
 * a pack without it.
 */
public final class MinimumsMenu {

    /** Wide enough for the longest GregTech coil name, so no row wraps. */
    private static final int LABEL_W = 118;
    private static final int STEP_W = 12;
    private static final int ROW_H = 12;
    private static final int PADDING = 3;

    private boolean open;
    private final Menu<?> menu;

    /**
     * Visibility is stored here, since only the toolbar button opens this menu. The canvas opens the other floating
     * panels, so their visibility is stored there.
     */
    public MinimumsMenu() {
        final Flow rows = Flow.column()
            .coverChildren()
            .childPadding(2);
        for (final ChartMinimums.Minimum minimum : ChartMinimums.all()) {
            rows.child(row(minimum));
        }
        // A fill on the menu has no effect: the menu sizes itself from its child and draws the theme
        // background behind it either way. The theme background is see-through, so the rows would be
        // unreadable over a dense chart.
        rows.padding(PADDING)
            .background(
                new Rectangle().color(PlannhColors.CONTEXT_BG.getColor()),
                new Rectangle().hollow()
                    .color(PlannhColors.CONTEXT_BORDER.getColor()));
        menu = new Menu<>().setEnabledIf(_ -> open)
            .coverChildren()
            .relativeToScreen()
            .child(rows);
    }

    /** The widget to parent to the screen's panel. Parented to the canvas, it would pan and zoom with the chart. */
    @Nonnull
    public Menu<?> widget() {
        return menu;
    }

    /**
     * @param below The screen row the toolbar ends at. Opening at the cursor would put the panel over the
     *              buttons after a click near the top of one, leaving both unreadable.
     */
    public void toggle(final int screenX, final int below) {
        open = !open;
        if (open) menu.pos(screenX, below);
    }

    @Nonnull
    private static IWidget row(final ChartMinimums.Minimum minimum) {
        return Flow.row()
            .coverChildren()
            .childPadding(2)
            .child(stepper("-", minimum, -1))
            // Coloured on the widget: TextWidget draws the key's text through its renderer, and a colour
            // set on the key never reaches it. The fallback theme colour is dark grey, unreadable on this
            // panel's dark background.
            .child(
                IKey.dynamicKey(
                    () -> IKey.str(
                        minimum.label() + " "
                            + minimum.name()
                                .apply(minimum.current(Plan.getActiveGraph()))))
                    .asWidget()
                    .color(PlannhColors.TEXT_WHITE.getColor())
                    .size(LABEL_W, ROW_H))
            .child(stepper("+", minimum, 1));
    }

    @Nonnull
    private static IWidget stepper(final String glyph, final ChartMinimums.Minimum minimum, final int by) {
        return new ButtonWidget<>().overlay(IKey.str(glyph))
            .size(STEP_W, ROW_H)
            .onMousePressed(_ -> {
                minimum.step(Plan.getActiveGraph(), by);
                PlanAPI.save();
                return true;
            });
    }
}
