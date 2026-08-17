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
 * The structure the active chart plans with: the knobs a node opens on when the user has said nothing
 * about it.
 *
 * <p>
 * One panel for the whole chart rather than a row on every node. A chart describes a factory at one
 * point in a world's progression, so the coils it can build is a fact about the chart; asking it of
 * each node is asking the same question fifty times. A node that differs still says so on its own row,
 * and a recipe that needs more than the chart offers raises its own node without being asked.
 *
 * <p>
 * The rows come from {@link ChartMinimums}, which the installed providers fill. This class names no
 * mod: GregTech is a compile-only dependency, and reaching for a coil here would make the whole
 * flowchart screen fail to open on a pack without it.
 */
public final class MinimumsMenu {

    /** Wide enough for the longest coil GregTech ships, so no row wraps onto a second line. */
    private static final int LABEL_W = 118;
    private static final int STEP_W = 12;
    private static final int ROW_H = 12;
    private static final int PADDING = 3;

    private boolean open;
    private final Menu<?> menu;

    /**
     * Holds its own visibility, so the toolbar button that toggles it is the only state there is. The
     * other floating panels keep theirs on the canvas because the canvas is what opens them; nothing
     * opens this one but the button.
     */
    public MinimumsMenu() {
        final Flow rows = Flow.column()
            .coverChildren()
            .childPadding(2);
        for (final ChartMinimums.Minimum minimum : ChartMinimums.all()) {
            rows.child(row(minimum));
        }
        // The fill goes on the row stack rather than on the menu around it, because the menu sizes
        // itself from its child and paints the theme's own background behind it either way. Its own
        // colour rather than the theme's: this floats over the chart, and a background the canvas
        // shows through leaves the rows unreadable over a dense one.
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

    /** The widget to hang off the screen's panel; never off the canvas, which pans and zooms. */
    @Nonnull
    public Menu<?> widget() {
        return menu;
    }

    /**
     * @param below the screen row the toolbar ends at. The panel opens there rather than under the
     *              cursor, because a click near the top of the button would otherwise put the panel
     *              over the buttons themselves, and both become unreadable.
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
            // Coloured on the widget, not on the key: TextWidget draws the key's text through its own
            // renderer and falls back to the theme's colour, which is the dark grey a light panel
            // wants and is unreadable on the dark one this panel draws. A colour set on the key never
            // reaches the renderer.
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
