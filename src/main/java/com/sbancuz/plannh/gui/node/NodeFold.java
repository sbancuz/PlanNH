package com.sbancuz.plannh.gui.node;

import java.util.function.BooleanSupplier;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.viewport.LocatedWidget;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.common.FlowchartFlow;

/**
 * Foldable aware area part of the configuration widget
 */
abstract class NodeFold extends FlowchartFlow {

    /** The gap between two rows of a fold, and either side of the rule between two folds. */
    protected static final int ROW_GAP = 4;

    protected final NodeWidget node;

    /** What the rows were built from: a change in it, or in the recipe, is a rebuild. */
    private Object builtWith;
    private boolean dirty = true;

    /**
     * @param open what decides whether the fold is showing. There is no null for one that always is:
     *             a fold that cannot be shut has nothing to be asked.
     */
    protected NodeFold(final NodeWidget node, final BooleanSupplier open) {
        super(GuiAxis.Y, node);
        this.node = node;
        builtWith = node.getData()
            .getExtractor();

        // The margin goes with the rows: a shut fold is skipped whole by the area, margin included, so
        // the gap between two folds is there only while both of them are open.
        fullWidth().coverChildrenHeight()
            .marginBottom(ROW_GAP)
            .setEnabledIf(_ -> open.getAsBoolean());

        onUpdateListener(w -> {
            if (!dirty && builtWith == node.getData()
                .getExtractor()) return;

            if (holdsFocus()) return;
            dirty = false;
            builtWith = node.getData()
                .getExtractor();

            rebuild();
        }, true);
    }

    /** The rows, from whatever they are a function of. Called on construction and on every rebuild. */
    protected abstract void rebuild();

    protected void commitEdit(final Runnable change) {
        final CanvasWidget canvas = node.getCanvas();
        PlanAPI.recordEdit(canvas.getGraph(), change);
        canvas.getGraph()
            .bumpVersion();
        PlanAPI.save();
    }

    /** Asks for a rebuild on the next update that finds no focused field. */
    protected void markStale() {
        dirty = true;
    }

    /**
     * Whether a field in this fold has the keyboard. A rebuild would take the field out of the tree
     * under the player, so it waits for one that does not.
     */
    private boolean holdsFocus() {
        final LocatedWidget focused = getContext().getFocusedWidget();
        for (IWidget w = focused == null ? null : focused.getElement(); w != null; w = w.getParent()) {
            if (w == this) return true;
        }
        return false;
    }
}
